let t=document.createElement("template");t.innerHTML=`
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
`;class e extends HTMLElement{constructor(){super();let e=this.attachShadow({mode:"open"});e.append(t.content.cloneNode(!0)),this.activeNode=e.querySelector("#active-node"),this.loadGraphButton=e.querySelector("#load-graph"),this.highlightButton=e.querySelector("#highlight-node"),this.loadGraph=this.loadGraph.bind(this),this.highlightActiveNode=this.highlightActiveNode.bind(this)}connectedCallback(){this.loadGraphButton.addEventListener("click",this.loadGraph),this.highlightButton.addEventListener("click",this.highlightActiveNode),this.loadGraph().catch(t=>console.error(t))}disconnectedCallback(){this.loadGraphButton.removeEventListener("click",this.loadGraph),this.highlightButton.removeEventListener("click",this.highlightActiveNode)}async loadGraph(){let t=await fetch("/api/graph");if(!t.ok)throw Error(`Graph request failed: ${t.status}`);let e=await t.text();this.dispatchGraphEvent("graph",e)}highlightActiveNode(){this.dispatchGraphEvent("graph-active",{node:this.activeNode.value.trim()})}dispatchGraphEvent(t,e){this.dispatchEvent(new CustomEvent(t,{detail:e,bubbles:!0,composed:!0}))}}customElements.define("lg4j-executor-test",e);
//# sourceMappingURL=webui.a37a1482.js.map
