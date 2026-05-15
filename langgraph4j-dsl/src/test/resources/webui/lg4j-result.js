const template = document.createElement('template');
template.innerHTML = `
  <style>
    :host {
      display: block;
      height: 100%;
      min-height: 0;
      color-scheme: light;
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      color: #17212f;
    }

    * {
      box-sizing: border-box;
    }

    .result {
      height: 100%;
      min-height: 0;
      display: grid;
      grid-template-rows: auto minmax(0, 1fr);
      gap: 8px;
      padding: 16px;
      background: #ffffff;
    }

    label {
      font-size: 12px;
      font-weight: 700;
      color: #334155;
    }

    textarea {
      width: 100%;
      min-height: 0;
      resize: none;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      background: #f8fafc;
      color: #17212f;
      padding: 10px;
      font: 12px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    }

    textarea:focus-visible {
      outline: 3px solid rgba(37, 99, 235, 0.25);
      outline-offset: 1px;
    }
  </style>
  <div class="result">
    <label for="dsl-source">DSL JSON</label>
    <textarea id="dsl-source" readonly spellcheck="false"></textarea>
  </div>
`;

export class LG4JResultElement extends HTMLElement {

  constructor() {
    super();
    const shadow = this.attachShadow({ mode: 'open' });
    shadow.append(template.content.cloneNode(true));
    this.sourceView = shadow.querySelector('#dsl-source');
    this.renderGraph = this.renderGraph.bind(this);
  }

  connectedCallback() {
    this.addEventListener('graph', this.renderGraph);
  }

  disconnectedCallback() {
    this.removeEventListener('graph', this.renderGraph);
  }

  renderGraph(event) {
    const source = event.detail;
    this.sourceView.value = JSON.stringify(JSON.parse(source), null, 2);
  }
}

customElements.define('lg4j-result', LG4JResultElement);
