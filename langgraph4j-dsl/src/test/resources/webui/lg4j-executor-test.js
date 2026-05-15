const template = document.createElement('template');
template.innerHTML = `
  <style>
    :host {
      display: block;
      height: 100%;
      min-height: 100vh;
      color-scheme: light;
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      color: #17212f;
    }

    * {
      box-sizing: border-box;
    }

    .executor {
      height: 100%;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 16px;
      background: #ffffff;
    }

    label {
      display: grid;
      gap: 6px;
      font-size: 12px;
      font-weight: 700;
      color: #334155;
    }

    label.source {
      min-height: 0;
      flex: 1 1 auto;
      grid-template-rows: auto minmax(0, 1fr);
    }

    textarea,
    input {
      width: 100%;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      background: #f8fafc;
      color: #17212f;
      font: 12px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    }

    textarea {
      min-height: 0;
      resize: none;
      padding: 10px;
    }

    input {
      height: 34px;
      padding: 0 10px;
    }

    button {
      height: 34px;
      border: 1px solid #2563eb;
      border-radius: 6px;
      background: #2563eb;
      color: #ffffff;
      font-weight: 700;
      cursor: pointer;
    }

    button:focus-visible,
    input:focus-visible,
    textarea:focus-visible {
      outline: 3px solid rgba(37, 99, 235, 0.25);
      outline-offset: 1px;
    }
  </style>
  <div class="executor">
    <label class="source">
      DSL JSON
      <textarea id="dsl-source" readonly spellcheck="false"></textarea>
    </label>
    <label>
      Active node
      <input id="active-node" type="text" placeholder="planner">
    </label>
    <button id="highlight-node" type="button">Highlight</button>
  </div>
`;

export class LG4JExecutorTestElement extends HTMLElement {

  constructor() {
    super();
    const shadow = this.attachShadow({ mode: 'open' });
    shadow.append(template.content.cloneNode(true));
    this.sourceView = shadow.querySelector('#dsl-source');
    this.activeNode = shadow.querySelector('#active-node');
    this.highlightButton = shadow.querySelector('#highlight-node');
    this.loadGraph = this.loadGraph.bind(this);
    this.highlightActiveNode = this.highlightActiveNode.bind(this);
  }

  connectedCallback() {
    this.highlightButton.addEventListener('click', this.highlightActiveNode);
    this.loadGraph().catch((caught) => console.error(caught));
  }

  disconnectedCallback() {
    this.highlightButton.removeEventListener('click', this.highlightActiveNode);
  }

  async loadGraph() {
    const response = await fetch('/api/graph');
    if (!response.ok) {
      throw new Error(`Graph request failed: ${response.status}`);
    }

    const source = await response.text();
    this.sourceView.value = JSON.stringify(JSON.parse(source), null, 2);
    this.dispatchGraphEvent('graph', source);
  }

  highlightActiveNode() {
    this.dispatchGraphEvent('graph-active', {
      node: this.activeNode.value.trim()
    });
  }

  dispatchGraphEvent(type, detail) {
    this.dispatchEvent(new CustomEvent(type, {
      detail,
      bubbles: true,
      composed: true
    }));
  }
}

customElements.define('lg4j-executor-test', LG4JExecutorTestElement);
