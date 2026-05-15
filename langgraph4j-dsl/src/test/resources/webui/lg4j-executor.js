const template = document.createElement('template');
template.innerHTML = `
  <style>
    :host {
      display: block;
      height: 100%;
      color-scheme: light;
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      color: #17212f;
    }

    * {
      box-sizing: border-box;
    }

    .executor {
      height: 100%;
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

    input {
      width: 100%;
      height: 34px;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      background: #f8fafc;
      color: #17212f;
      padding: 0 10px;
      font: 12px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
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

    button.secondary {
      border-color: #cbd5e1;
      background: #ffffff;
      color: #17212f;
    }

    button:focus-visible,
    input:focus-visible {
      outline: 3px solid rgba(37, 99, 235, 0.25);
      outline-offset: 1px;
    }
  </style>
  <div class="executor">
    <button id="load-graph" class="secondary" type="button">Reload graph</button>
    <label>
      Active node
      <input id="active-node" type="text" placeholder="planner">
    </label>
    <button id="highlight-node" type="button">Highlight</button>
  </div>
`;

export class LG4JExecutorElement extends HTMLElement {

  constructor() {
    super();
    const shadow = this.attachShadow({ mode: 'open' });
    shadow.append(template.content.cloneNode(true));
    this.activeNode = shadow.querySelector('#active-node');
    this.loadGraphButton = shadow.querySelector('#load-graph');
    this.highlightButton = shadow.querySelector('#highlight-node');
    this.loadGraph = this.loadGraph.bind(this);
    this.highlightActiveNode = this.highlightActiveNode.bind(this);
  }

  connectedCallback() {
    this.loadGraphButton.addEventListener('click', this.loadGraph);
    this.highlightButton.addEventListener('click', this.highlightActiveNode);
    this.loadGraph().catch((caught) => console.error(caught));
  }

  disconnectedCallback() {
    this.loadGraphButton.removeEventListener('click', this.loadGraph);
    this.highlightButton.removeEventListener('click', this.highlightActiveNode);
  }

  async loadGraph() {
    const response = await fetch('/api/graph');
    if (!response.ok) {
      throw new Error(`Graph request failed: ${response.status}`);
    }

    const source = await response.text();
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

customElements.define('lg4j-executor', LG4JExecutorElement);
