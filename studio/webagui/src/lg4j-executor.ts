import {
  css,
  CSSResult,
  html,
  LitElement,
  type PropertyDeclarations,
} from "lit";
import { type BaseEvent, HttpAgent, type RunAgentInput } from "@ag-ui/client";
import { type Subscription } from "rxjs";

import { debug } from "./debug";
import type {
  ArgumentMetadata,
  Instance,
  ResultData,
  UpdatedState,
} from "./types";

const _DBG = debug({ on: true, topic: "LG4JExecutor" });
const _DBGW = debug({ on: true, topic: "LG4JViewerExecutor" });

/**
 * Asynchronously waits for a specified number of milliseconds.
 *
 * @param {number} ms - The number of milliseconds to wait.
 * @returns {Promise<void>} A promise that resolves after the specified delay.
 */
const delay = async (
  ms: number,
): Promise<void> => (new Promise((resolve) => setTimeout(resolve, ms)));

class LG4JFetchError extends Error {
  /**
   * @param {Response} response
   */
  constructor(response: Response) {
    super(response.statusText || "Retrieve data error");
  }
}

/**
 * LG4JInputElement is a custom web component that extends LitElement.
 * It provides a styled input container with a placeholder.
 *
 * @class
 * @extends {LitElement}
 */
export class LG4JExecutorElement extends LitElement {
  /**
   * Styles applied to the component.
   *
   * @static
   * @type {Array<CSSResult>}
   */
  static styles: CSSResult[] = [css`
    :host {
      display: block;
      color: #e5e7eb;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont,
        "Segoe UI", sans-serif;
    }

    .container {
      display: flex;
      flex-direction: column;
      row-gap: 5px;
    }

    .commands {
      display: flex;
      flex-direction: row;
      column-gap: 10px;
      align-items: center;
    }

    .item1 {
      flex-grow: 2;
    }
    .item2 {
      flex-grow: 2;
    }
    .item3 {
      flex-grow: 2;
    }

    textarea {
      min-height: 3rem;
      width: 100%;
      box-sizing: border-box;
      padding: 0.75rem 1rem;
      resize: vertical;
      border: 1px solid #38bdf8;
      border-radius: 0.5rem;
      color: #e5e7eb;
      background: #111827;
      font: inherit;
      line-height: 1.4;
      outline: none;
    }

    textarea:focus {
      border-color: #7dd3fc;
      box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.18);
    }

    button {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      min-height: 0.75rem;
      padding: 0.65rem 1rem;
      margin-top: 0.25rem;
      border: 1px solid transparent;
      border-radius: 0.5rem;
      color: #ffffff;
      font: inherit;
      font-weight: 700;
      cursor: pointer;
      transition: background 0.15s ease, border-color 0.15s ease, opacity 0.15s
        ease;
    }

    button:disabled {
      cursor: not-allowed;
      opacity: 0.45;
    }

    .primary {
      background: #2563eb;
    }

    .primary:not(:disabled):hover {
      background: #1d4ed8;
    }

    .secondary {
      background: #7c3aed;
    }

    .secondary:not(:disabled):hover {
      background: #6d28d9;
    }

    .danger {
      background: #dc2626;
    }

    .danger:not(:disabled):hover {
      background: #b91c1c;
    }

    .icon {
      width: 0.75rem;
      height: 0.75rem;
      flex-shrink: 0;
      stroke: currentColor;
    }

    dialog {
      width: min(32rem, calc(100vw - 2rem));
      padding: 0;
      border: 0;
      border-radius: 0.75rem;
      color: #e5e7eb;
      background: #111827;
      box-shadow: 0 24px 64px rgba(0, 0, 0, 0.45);
    }

    dialog::backdrop {
      background: rgba(15, 23, 42, 0.72);
    }

    .modal-box {
      position: relative;
      padding: 1.5rem;
    }

    .close-button {
      position: absolute;
      top: 0.5rem;
      right: 0.5rem;
      width: 2rem;
      height: 2rem;
      min-height: 2rem;
      padding: 0;
      border-radius: 999px;
      color: #e5e7eb;
      background: transparent;
    }

    .close-button:hover {
      background: rgba(255, 255, 255, 0.08);
    }

    .error-content {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0 2rem 0 0;
      color: #f87171;
    }

    #error_message {
      margin: 0;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-weight: 700;
    }
  `];

  /**
   * Properties of the component.
   *
   * @static
   * @type { import('lit').PropertyDeclarations }
   */
  static properties: PropertyDeclarations = {
    url: { type: String, reflect: true },
    _executing: { state: true },
  };

  declare url: string | null;
  declare _executing: boolean;

  /**
   * current selected thread
   *
   * @type {string|undefined} - thread id
   */
  _selectedThread: string | undefined = undefined;

  /**
   * current state for update
   *
   * @type {UpdatedState|null}
   */
  #updatedState: UpdatedState | null = null;

  /**
   * Instance id
   *
   * @type {string|undefined} - instance id
   */
  #instanceId: string | undefined = undefined;

  private formMetaData: ArgumentMetadata[] = [];
  private subscription: Subscription | null = null;

  /**
   * Creates an instance of LG4JInputElement.
   *
   * @constructor
   */
  constructor() {
    super();
    this.url = null;
    this._executing = false;
  }

  /**
   * if url is not set, return context path
   *
   * @returns {string} - context path
   */
  get _contextPath(): string {
    // vadidate url
    const url = new URL(
      this.url || `${window.location.protocol}//${window.location.host}`,
    );

    const pathName = ((this.url)
      ? url.toString() // if url is set, use it as is
      : url.pathname).replace(/\/+$/, "");

    return pathName.replace(/\/+$/, ""); // remove trailing slash
  }

  #startExecution() {
    this._executing = true;
    this.dispatchEvent(
      new CustomEvent("state-updated", {
        detail: "start",
        bubbles: true,
        composed: true,
        cancelable: true,
      }),
    );
  }

  /**
   * @param {[ string, UpdatedState & { next: string } ]|Error|null} result
   */
  #stopExecution(
    result: [string, UpdatedState & { next: string }] | Error | null,
  ) {
    this._executing = false;

    // NO ACTION
    if (!result) {
      return;
    }

    // ON ERROR
    if (result instanceof Error) {
      this.dispatchEvent(
        new CustomEvent("state-updated", {
          detail: "error",
          bubbles: true,
          composed: true,
          cancelable: true,
        }),
      );
      return;
    }
    // ON SUCCESS
    const [_, { node }] = result;

    // Asuume that flow is interrupted if last node is different by last node (__END__)
    this.dispatchEvent(
      new CustomEvent("state-updated", {
        detail: (node !== "__END__") ? "interrupted" : "stop",
        bubbles: true,
        composed: true,
        cancelable: true,
      }),
    );
  }

  /**
   * Event handler for the 'update slected thread' event.
   *
   * @param {CustomEvent<string>} e - The event object containing the updated data.
   */
  #onThreadUpdated(e: CustomEvent<string>) {
    _DBG("thread-updated", e.detail);
    this._selectedThread = e.detail;
    this.#updatedState = null;
    this.requestUpdate();
  }

  /**
   * @param {CustomEvent<UpdatedState>} e - The event object containing the result data.
   */
  #onNodeUpdated(e: CustomEvent<UpdatedState>) {
    _DBG("onNodeUpdated", e);
    this.#updatedState = e.detail;
    this.requestUpdate();
  }

  /**
   * Lifecycle method called when the element is added to the document's DOM.
   */
  connectedCallback() {
    super.connectedCallback();

    // @ts-ignore
    this.addEventListener("thread-updated", this.#onThreadUpdated);
    // @ts-ignore
    this.addEventListener("node-updated", this.#onNodeUpdated);

    this._callInit();
  }

  disconnectedCallback() {
    super.disconnectedCallback();

    // @ts-ignore
    this.removeEventListener("thread-updated", this.#onThreadUpdated);
    // @ts-ignore
    this.removeEventListener("node-updated", this.#onNodeUpdated);
  }

  /**
   * @param {string} detail
   */
  #requestShowError(detail: string) {
    const elem = this.shadowRoot?.getElementById("error_dialog");
    if (elem && "showModal" in elem) {
      const msgElem = elem.querySelector("#error_message");
      if (msgElem) {
        msgElem.textContent = detail;
      }
      //@ts-ignore
      elem.showModal();

      // if( timeout ) {
      //   await delay(timeout)
      //   //@ts-ignore
      //   elem.close()
      // }
    }
  }

  // PROTECTED METHOD
  async _callInit() {
    let initUrl = `${this._contextPath}/init${window.location.search}`;
    _DBG("initUrl", initUrl);

    const initResponse = await fetch(initUrl, {
      method: "GET",
      credentials: "include",
    });

    if (!initResponse.ok) {
      this.#requestShowError(initResponse.statusText);
      return null;
    }

    const instance: Instance = await initResponse.json();

    _DBG("initData", instance);

    this.dispatchEvent(
      new CustomEvent("init", {
        detail: instance,
        bubbles: true,
        composed: true,
        cancelable: true,
      }),
    );

    this.#instanceId = instance.id;
    this.formMetaData = instance.args;
    // this.#nodes = initData.nodes
    this.requestUpdate();
  }

  async #callResume() {
    this.#startExecution();
    let result = null;

    try {
      // if (this.test) {
      //   await test.callSubmitAction(this, this.#selectedThread);
      //   return
      // }

      result = await this.#callResumeAction();
    } catch (err) {
      if (err instanceof Error) {
        this.#requestShowError(err.message);
        result = err;
      }
    } finally {
      this.#stopExecution(result);
    }
  }

  async #callResumeAction() {
  }

  /**
   * Called when the user clicks the stop button. Dispatches a 'stop' event
   * and attempts to cancel execution locally.
   */
  async #callCancel() {
    // If not executing, ignore
    if (!this._executing) return;

    const execResponse = await fetch(
      `${this._contextPath}/stream/${this.#instanceId}?thread=${this._selectedThread}&cancel=true`,
      {
        method: "DELETE", // *GET, POST, PUT, DELETE, etc.
        credentials: "include",
      },
    );

    if (!execResponse.ok) {
      throw new LG4JFetchError(execResponse);
    }

    const event = new CustomEvent<[string, ResultData]>("result", {
      detail: [this._selectedThread!, {
        node: "",
        next: "",
        state: {},
        subgraphNode: undefined,
        cancelled: true,
      }],
      bubbles: true,
      composed: true,
      cancelable: true,
    });
    this.dispatchEvent(event);
  }

async _callSubmit() {
    _DBG("callSubmit");
    
    // Get input as object
    const result: Record<string, any> = {};
    const data: Record<string, any> = this.formMetaData.reduce((acc, md) => {
      const { name, type } = md;
      const elem = this.shadowRoot?.getElementById(name);

      switch (type) {
        case "STRING":
          //@ts-ignore
          acc[name] = elem?.value;
          break;
        case "IMAGE":
          //@ts-ignore
          acc[name] = elem?.value;
          break;
      }

      return acc;
    }, result);

    
    this.#startExecution();

    const catchError = (error: unknown) => {
      let result:Error;
      if (error instanceof Error) {
        result = error;
      } else {
        result = new Error(JSON.stringify(error));
      }
      this.#requestShowError(result.message);
      this.#stopExecution(result);
    };

    try {

      let agent = new HttpAgent({
        url:
          `${this._contextPath}/stream/${this.#instanceId}?thread=${this._selectedThread}`,
        fetch: (url, init) => fetch(url, { ...init, credentials: "include" }),
      });

      let input: RunAgentInput = {
        runId: "run1",
        threadId: this._selectedThread!,
        state: data,
        messages: [],
        tools: [],
        context: [],
      };

      this.subscription = agent.run(input).subscribe({
        next: (event) => {
          _DBG("Received event: ", JSON.stringify(event));

          const e: CustomEvent<BaseEvent> = new CustomEvent("agui-event", {
            detail: event,
            bubbles: true,
            composed: true,
            cancelable: true,
          });
          this.dispatchEvent(e);
        },
        error: (error: unknown) => catchError(error),
        complete: () => this.#stopExecution(null),
      });
    } catch (error: unknown) {
      catchError(error);
    }
  }

  /**
   * Renders the HTML template for the component.
   *
   * @returns The rendered HTML template.
   */
  render() {
    return html`
      <div class="container">
        ${this.formMetaData.map(({ name, type }) => {
          switch (type) {
            case "STRING":
              return html`<textarea id="${name}" placeholder="${name}"></textarea>`;
            case "IMAGE":
              return html`<lg4j-image-uploader id="${name}"></lg4j-image-uploader>`;
          }
        })}
        <div class="commands">
          <button id="submit" ?disabled=${this._executing} @click="${this
            ._callSubmit}" class="primary item1">Submit</button>
          <button id="resume" ?disabled=${!this.#updatedState ||
            this._executing} @click="${this
            .#callResume}" class="secondary item2">
          Resume ${this.#updatedState
            ? "(from " + this.#updatedState?.node + ")"
            : ""}
          </button>
          <button id="cancel" @click="${this.#callCancel}" ?disabled=${!this
            ._executing} class="danger item3" aria-label="Stop">
            Cancel
            <svg xmlns="http://www.w3.org/2000/svg" class="icon" fill="none" viewBox="0 0 24 24">
              <rect x="5" y="5" width="14" height="14" rx="2" ry="2" />
            </svg>
          </button>
        </div>
      </div>
      <!--
      ==============
      ERROR DIALOG
      ==============
      -->
      <dialog id="error_dialog">
        <div class="modal-box">
          <form method="dialog">
            <button class="close-button">x</button>
          </form>
          <div class="error-content">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              class="icon"
              fill="none"
              viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <p id="error_message">ERROR</p>
          </div>
        </div>
      </dialog>
    `;
  }
}

class LG4JViewerExecutorElement extends LG4JExecutorElement {
  static styles = [
    ...LG4JExecutorElement.styles,
    css`
      .container {
        display: none;
      }
    `,
  ];

  connectedCallback() {
    this.hidden = true;
    this.setAttribute("aria-hidden", "true");
    super.connectedCallback();
  }

  get _contextPath() {
    return super._contextPath.concat("/viewer");
  }

  async _callInit() {
    _DBGW("_callInit");
    const result = await super._callInit();

    setTimeout(async () => {
      this._selectedThread = "default";
      await this._callSubmit();
    }, 1000);

    return result;
  }
}

window.customElements.define("lg4j-executor", LG4JExecutorElement);
window.customElements.define("lg4j-viewer-executor", LG4JViewerExecutorElement);
