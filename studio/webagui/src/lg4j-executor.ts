import { HttpAgent, EventType, type BaseEvent, type RunAgentInput } from '@ag-ui/client';
import { LitElement, css, html } from 'lit';
import { repeat } from 'lit/directives/repeat.js';

/** Executes one workflow run and displays every event returned by the AG-UI SDK. */
export class LG4JExecutorElement extends LitElement {
  static properties = {
    url: { type: String },
    agentId: { type: String, attribute: 'agent-id' },
    prompt: { state: true },
    stateInput: { state: true },
    events: { state: true },
    status: { state: true },
    error: { state: true },
    running: { state: true },
  };

  declare url: string;
  declare agentId: string;
  declare prompt: string;
  declare stateInput: string;
  declare events: BaseEvent[];
  declare status: string;
  declare error: string;
  declare running: boolean;

  private agent?: HttpAgent;
  private subscription?: { unsubscribe(): void };

  constructor() {
    super();
    this.url = 'http://localhost:8081';
    this.agentId = '';
    this.prompt = '';
    this.stateInput = '{}';
    this.events = [];
    this.status = 'Ready';
    this.error = '';
    this.running = false;
  }

  static styles = css`
    :host { display: block; font: inherit; color: #e5e7eb; }
    form { display: grid; gap: 1rem; }
    label { display: grid; gap: 0.4rem; }
    input, textarea, button { font: inherit; border-radius: 0.5rem; }
    input, textarea {
      box-sizing: border-box; width: 100%; padding: 0.75rem;
      color: #e5e7eb; background: #111827; border: 1px solid #64748b;
    }
    textarea { resize: vertical; }
    input:focus-visible, textarea:focus-visible, button:focus-visible {
      outline: 2px solid #38bdf8; outline-offset: 2px;
    }
    .commands { display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem; }
    button { padding: 0.65rem 1rem; border: 0; color: white; background: #2563eb; cursor: pointer; }
    button.secondary { background: #475569; }
    button:disabled { opacity: 0.5; cursor: not-allowed; }
    .error { color: #fca5a5; overflow-wrap: anywhere; }
    ol { padding-left: 1.75rem; }
    li { padding: 0.75rem; margin-bottom: 0.75rem; background: #111827; border-radius: 0.5rem; }
    pre { margin-bottom: 0; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 0.85rem; }
    .empty { color: #94a3b8; }
  `;

  /** A fresh thread and run keep separate executions independent. */
  createRunInput(): RunAgentInput {
    const state: unknown = JSON.parse(this.stateInput);
    if (state === null || typeof state !== 'object' || Array.isArray(state)) {
      throw new Error('Initial state must be a JSON object.');
    }
    return {
      threadId: crypto.randomUUID(),
      runId: crypto.randomUUID(),
      state,
      messages: this.prompt.trim()
        ? [{ id: crypto.randomUUID(), role: 'user', content: this.prompt }]
        : [],
      tools: [],
      context: [],
      forwardedProps: {},
    };
  }

  execute(): void {
    if (this.running) return;
    this.error = '';
    try {
      const agentId = this.agentId.trim();
      if (!agentId) throw new Error('Enter an agent ID.');
      const input = this.createRunInput();
      const endpoint = `${this.url.replace(/\/+$/, '')}/stream/${encodeURIComponent(agentId)}`;
      this.agent = new HttpAgent({ url: endpoint, agentId });
      this.events = [];
      this.status = 'Running';
      this.running = true;
      // Subscribe to the SDK's raw event stream so all event types remain visible.
      this.subscription = this.agent.run(input).subscribe({
        next: (event) => {
          this.events = [...this.events, event];
          if (event.type === EventType.RUN_ERROR) {
            this.error = String((event as BaseEvent & { message: string }).message);
            this.status = 'Failed';
          }
        },
        error: (error: unknown) => {
          this.error = error instanceof Error ? error.message : String(error);
          this.status = 'Failed';
          this.running = false;
        },
        complete: () => {
          this.status = this.error ? 'Failed' : 'Completed';
          this.running = false;
        },
      });
    } catch (error) {
      this.error = error instanceof Error ? error.message : String(error);
      this.status = 'Failed';
      this.running = false;
    }
  }

  cancel(): void {
    if (!this.running) return;
    this.subscription?.unsubscribe();
    this.agent?.abortRun();
    this.subscription = undefined;
    this.agent = undefined;
    this.running = false;
    this.status = 'Cancelled';
  }

  override disconnectedCallback(): void {
    this.cancel();
    super.disconnectedCallback();
  }

  override render() {
    return html`
      <form @submit=${(event: SubmitEvent) => { event.preventDefault(); this.execute(); }}>
        <label>Agent ID
          <input name="agentId" required .value=${this.agentId} ?disabled=${this.running}
            @input=${(event: Event) => { this.agentId = (event.target as HTMLInputElement).value; }} />
        </label>
        <label>Message (optional)
          <textarea name="prompt" rows="3" .value=${this.prompt} ?disabled=${this.running}
            @input=${(event: Event) => { this.prompt = (event.target as HTMLTextAreaElement).value; }}></textarea>
        </label>
        <label>Initial state (JSON object)
          <textarea name="state" rows="4" .value=${this.stateInput} ?disabled=${this.running}
            @input=${(event: Event) => { this.stateInput = (event.target as HTMLTextAreaElement).value; }}></textarea>
        </label>
        <div class="commands">
          <button type="submit" ?disabled=${this.running}>Run workflow</button>
          <button type="button" class="secondary" ?disabled=${!this.running} @click=${this.cancel}>Cancel</button>
          <span role="status">${this.status}</span>
        </div>
      </form>
      ${this.error ? html`<p class="error" role="alert">${this.error}</p>` : ''}
      <h2>Events (${this.events.length})</h2>
      ${this.events.length === 0
        ? html`<p class="empty">Events will appear here when a workflow runs.</p>`
        : html`<ol aria-label="AG-UI events">${repeat(this.events, (_, index) => index,
          (event) => html`<li><strong>${event.type}</strong><pre>${JSON.stringify(event, null, 2)}</pre></li>`
        )}</ol>`}
    `;
  }
}

customElements.define('lg4j-executor', LG4JExecutorElement);

declare global {
  interface HTMLElementTagNameMap {
    'lg4j-executor': LG4JExecutorElement;
  }
}
