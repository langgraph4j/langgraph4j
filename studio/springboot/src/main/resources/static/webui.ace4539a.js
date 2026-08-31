var e=globalThis,t={},i={},s=e.parcelRequire0031;null==s&&((s=function(e){if(e in t)return t[e].exports;if(e in i){var s=i[e];delete i[e];var n={id:e,exports:{}};return t[e]=n,s.call(n.exports,n,n.exports),n.exports}var r=Error("Cannot find module '"+e+"'");throw r.code="MODULE_NOT_FOUND",r}).register=function(e,t){i[e]=t},e.parcelRequire0031=s),s.register;var n=s("800sp");let r=(0,s("8uVid").debug)({on:!0,topic:"LG4JWorkbench"});class l extends n.LitElement{static styles=[(0,n.css)`
    :host {
      display: block;
      min-height: 100vh;
      color: #e5e7eb;
      background: #0f172a;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }

    .shell {
      height: 100vh;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .navbar {
      display: flex;
      align-items: center;
      gap: 1rem;
      min-height: 1rem;
      height: 2.1rem;
      padding: 0 1rem;
      background: #111827;
      border-bottom: 1px solid #1f2937;
      flex-shrink: 0;
    }

    .title {
      display: inline-flex;
      align-items: center;
      min-height: 3rem;
      padding: 0 0.5rem;
      color: #f9fafb;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-weight: 700;
      text-decoration: none;
    }

    .status {
      display: flex;
      align-items: center;
      flex: 1;
      min-width: 0;
      margin-left: 2.5rem;
    }

    #message {
      margin-left: 1rem;
      font-style: italic;
      color: #cbd5e1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .toggle-results {
      width: 1.5rem;
      height: 1.5rem;
      padding: 0;
      display: inline-grid;
      place-items: center;
      flex-shrink: 0;
      border: 1px solid #374151;
      border-radius: 0.375rem;
      color: #e5e7eb;
      background: #1f2937;
      cursor: pointer;
      line-height: 0;
    }

    .toggle-results:hover {
      color: #ffffff;
      background: #273548;
      border-color: #4b5563;
    }

    .toggle-results:focus-visible {
      outline: 2px solid #60a5fa;
      outline-offset: 2px;
    }

    .toggle-results svg {
      width: 1rem;
      height: 1rem;
      display: block;
    }

    .hidden {
      display: none;
    }

    .spinner {
      width: 1rem;
      height: 1rem;
      border: 3px solid rgba(96, 165, 250, 0.25);
      border-top-color: #60a5fa;
      border-radius: 999px;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }

    .layout {
      flex: 1;
      min-height: 0;
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      column-gap: 0.5rem;
      padding: 0.5rem;
    }

    .layout.results-hidden {
      grid-template-columns: minmax(0, 1fr);
    }

    .left-column {
      min-height: 0;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .graph-panel {
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

    .result-panel {
      min-height: 0;
      overflow: hidden;
    }

    .result-panel[hidden] {
      display: none;
    }

    .executor-panel {
      flex-shrink: 0;
      min-height: 0;
      overflow: auto;
    }
  `];static properties={title:{},resultPanelVisible:{type:Boolean,converter:{fromAttribute:e=>"false"!==e}}};constructor(){super(),this.resultPanelVisible=!1}#e(){this.resultPanelVisible=!this.resultPanelVisible}#t(e,t){let{type:i,detail:s}=e;t||(t=i.split("-")[0]),r("routeEvent",i,t);let n=new CustomEvent(i,{detail:s}),l=this.querySelector(`[slot="${t}"]`);l?l.dispatchEvent(n):console.error(`slot '${t}' not found!`)}#i(e){let{graph:t,title:i,threads:s}=e.detail;this.#t(new CustomEvent("graph",{detail:t})),this.#t(new CustomEvent("init-threads",{detail:s}),"result"),i&&(this.title=i,this.requestUpdate())}#s(e){r("got updated event",e),this.#t(new CustomEvent(`${e.type}`,{detail:e.detail}),"executor")}#n(e){let t=this.shadowRoot?.getElementById("message");t&&(t.textContent=e)}#r(e){this.#n(e.detail.node),this.#t(e)}#l(e){let t=this.shadowRoot?.getElementById("spinner");if(t){if("start"===e.detail)return void t.classList.remove("hidden");t.classList.add("hidden"),"interrupted"===e.detail&&this.#n("INTERRUPTED")}this.#t(e,"result"),this.#t(e,"graph")}connectedCallback(){super.connectedCallback(),this.addEventListener("init",this.#i),this.addEventListener("result",this.#t),this.addEventListener("graph-active",this.#r),this.addEventListener("thread-updated",this.#s),this.addEventListener("node-updated",this.#s),this.addEventListener("state-updated",this.#l)}disconnectedCallback(){super.disconnectedCallback(),this.removeEventListener("state-updated",this.#l),this.removeEventListener("node-updated",this.#s),this.removeEventListener("thread-updated",this.#s),this.removeEventListener("graph-active",this.#r),this.removeEventListener("result",this.#t),this.removeEventListener("init",this.#i)}render(){let e=this.resultPanelVisible?"Hide result panel":"Show result panel";return(0,n.html)`
<div class="shell">

  <div class="navbar">

    <div>
      <a class="title">${this.title}</a>
    </div>

    <div class="status">
      <span id="spinner" class="hidden spinner"></span>
      <span id="message"></span>
    </div>

    <button
      class="toggle-results"
      type="button"
      title="${e}"
      aria-label="${e}"
      aria-expanded="${this.resultPanelVisible}"
      @click="${this.#e}">
      <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path d="M4 7h16M4 12h16M4 17h16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"></path>
      </svg>
    </button>

</div>

  <div class="layout ${this.resultPanelVisible?"":"results-hidden"}">    
    <div class="left-column">
      <div class="graph-panel"><slot name="graph">LEFT</slot></div>
      <div class="executor-panel"><slot name="executor">BOTTOM</slot></div>
    </div>
    <div class="result-panel" ?hidden="${!this.resultPanelVisible}"><slot name="result">RIGHT</slot></div>
  </div>
</div>
    `}}window.customElements.define("lg4j-workbench",l);
//# sourceMappingURL=webui.ace4539a.js.map
