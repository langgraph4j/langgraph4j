const template = document.createElement('template');
template.innerHTML = `
  <style>
    :host {
      display: block;
      width: 100%;
      min-height: 100vh;
      color-scheme: light;
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: #f8fafc;
      color: #17212f;
    }

    * {
      box-sizing: border-box;
    }

    .workbench {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(280px, 28vw);
      width: 100%;
      min-height: 100vh;
    }

    .side,
    .result,
    .executor {
      min-width: 0;
      min-height: 100vh;
    }

    .graph {
      flex: 1;
      min-height: 0;
      display: flex;
      border: 1px solid #d1d5db;
      overflow: hidden;
    }

    slot[name="graph"] {
      display: block;
      flex: 1;
      min-width: 0;
      min-height: 0;
    }

    ::slotted([slot="graph"]) {
      display: block;
      width: 100%;
      height: 100%;
      min-width: 0;
      min-height: 0;
    }

    .side {
      display: grid;
      grid-template-rows: minmax(0, 1fr) minmax(180px, 32vh);
      border-left: 1px solid #d8dee8;
    }

    .result,
    .executor {
      min-height: 0;
      background: #ffffff;
    }

    .executor {
      border-top: 1px solid #d8dee8;
    }

    slot[name="result"]::slotted(*) {
      width: 100%;
      height: 100%;
      min-height: 0;
    }

    slot[name="executor"]::slotted(*) {
      width: 100%;
      height: 100%;
      min-height: 0;
    }
  </style>
  <div class="workbench">
    <section class="graph">
      <slot name="graph"></slot>
    </section>
    <aside class="side">
      <section class="result">
        <slot name="result"></slot>
      </section>
      <section class="executor">
        <slot name="executor"></slot>
      </section>
    </aside>
  </div>
`;

export class LG4JWorkbenchElement extends HTMLElement {

  constructor() {
    super();
    this.attachShadow({ mode: 'open' }).append(template.content.cloneNode(true));
    this.forwardGraph = this.forwardGraph.bind(this);
    this.forwardGraphActive = this.forwardGraphActive.bind(this);
  }

  connectedCallback() {
    this.addEventListener('graph', this.forwardGraph);
    this.addEventListener('graph-active', this.forwardGraphActive);
    this.addEventListener('graph-acive', this.forwardGraphActive);
  }

  disconnectedCallback() {
    this.removeEventListener('graph', this.forwardGraph);
    this.removeEventListener('graph-active', this.forwardGraphActive);
    this.removeEventListener('graph-acive', this.forwardGraphActive);
  }

  forwardGraph(event) {
    this.dispatchGraphEvent('graph', event.detail, event);
  }

  forwardGraphActive(event) {
    this.dispatchGraphEvent('graph-active', event.detail, event);
  }

  dispatchGraphEvent(type, detail, originalEvent) {
    if (originalEvent.target === this.graphElement || originalEvent.target === this.resultElement) {
      return;
    }

    originalEvent.stopPropagation();
    this.graphElement?.dispatchEvent(new CustomEvent(type, { detail }));
    if (type === 'graph') {
      this.resultElement?.dispatchEvent(new CustomEvent(type, { detail }));
    }
  }

  get graphElement() {
    return this.querySelector('[slot="graph"]');
  }

  get resultElement() {
    return this.querySelector('[slot="result"]');
  }
}

customElements.define('lg4j-workbench', LG4JWorkbenchElement);
