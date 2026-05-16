let e=document.createElement("template");e.innerHTML=`
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
`;class t extends HTMLElement{constructor(){super();let t=this.attachShadow({mode:"open"});t.append(e.content.cloneNode(!0)),this.sourceView=t.querySelector("#dsl-source"),this.renderGraph=this.renderGraph.bind(this)}connectedCallback(){this.addEventListener("graph",this.renderGraph)}disconnectedCallback(){this.removeEventListener("graph",this.renderGraph)}renderGraph(e){let t=e.detail;this.sourceView.value=JSON.stringify(JSON.parse(t),null,2)}}customElements.define("lg4j-result-test",t);
//# sourceMappingURL=webui.5f610cc3.js.map
