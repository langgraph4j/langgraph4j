var e=globalThis,t={},s={},i=e.parcelRequire0031;null==i&&((i=function(e){if(e in t)return t[e].exports;if(e in s){var i=s[e];delete s[e];var a={id:e,exports:{}};return t[e]=a,i.call(a.exports,a,a.exports),a.exports}var r=Error("Cannot find module '"+e+"'");throw r.code="MODULE_NOT_FOUND",r}).register=function(e,t){s[e]=t},e.parcelRequire0031=i),i.register;var a=i("800sp");class r{items;constructor(e=[]){this.items=e}push(e){return this.items.unshift(e)}pop(){return this.items.shift()}peek(){return this.items[0]}get elements(){return this.items}isEmpty(){return 0===this.items.length}get size(){return this.items.length}clear(){this.items=[]}}var d=i("8uVid");let n=(0,d.debug)({on:!0,topic:"LG4JResult"});(0,d.debug)({on:!1,topic:"LG4JResult"});class l extends a.LitElement{static styles=[(0,a.css)`
    :host {
      display: block;
      height: 100%;
      color: #e5e7eb;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }

    json-viewer {
      --font-size: var(--lg4j-workbench-font-size, 12px);
    }

    .result-root {
      height: 100%;
      min-height: 0;
      display: flex;
      flex-direction: column;
    }

    .tabs {
      display: flex;
      align-items: center;
      gap: 0.25rem;
      border-bottom: 1px solid #374151;
      overflow-x: auto;
      flex-shrink: 0;
    }

    .tab {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      height: 1.5rem;
      padding: 0 0.75rem;
      border-bottom: 2px solid transparent;
      color: #cbd5e1;
      text-decoration: none;
      cursor: pointer;
      white-space: nowrap;
    }

    .tab:hover,
    .tab-active {
      color: #ffffff;
      border-bottom-color: #60a5fa;
    }

    .add-tab {
      padding: 0 0.75rem;
    }

    .add-tab svg {
      display: block;
    }

    .results-panel {
      flex: 1;
      min-height: 0;
      max-height: 95%;
      overflow: auto;
      padding: 0.5rem;
      background: #475569;
    }

    .alert {
      padding: 1rem;
      border: 1px solid #f59e0b;
      border-radius: 0.5rem;
      color: #fef3c7;
      background: rgba(245, 158, 11, 0.16);
    }

    details {
      margin-bottom: 0.5rem;
      border-radius: 0.5rem;
      overflow: hidden;
      background: #1f2937;
    }

    details.execution {
      background: #334155;
    }

    summary {
      display: flex;
      align-items: center;
      min-height: 3rem;
      padding: 0rem 2.5rem 0rem 1rem;
      font-weight: 700;
      cursor: pointer;
      list-style: none;
      position: relative;
    }

    summary::-webkit-details-marker {
      display: none;
    }

    summary::after {
      content: "+";
      position: absolute;
      right: 1rem;
      font-size: var(--lg4j-workbench-font-size, 12px);
      line-height: 1;
    }

    details[open] > summary::after {
      content: "-";
    }

    .details-content {
      padding: 0 1rem 1rem;
    }

    table {
      width: 100%;
      border-collapse: collapse;
    }

    td {
      padding: 0.35rem 0;
      vertical-align: top;
    }
  `];static properties={};threadMap=new Map;#e;get selectedTab(){return this.#e}set selectedTab(e){this.#e=e,this.dispatchEvent(new CustomEvent("thread-updated",{detail:e,bubbles:!0,composed:!0,cancelable:!0}))}constructor(){super()}connectedCallback(){super.connectedCallback(),this.addEventListener("result",this.#t),this.addEventListener("init-threads",this.#s),this.addEventListener("node-updated",this.#i),this.addEventListener("state-updated",this.#a)}disconnectedCallback(){super.disconnectedCallback(),this.removeEventListener("state-updated",this.#a),this.removeEventListener("result",this.#t),this.removeEventListener("init-threads",this.#s),this.removeEventListener("node-updated",this.#i)}#s=e=>{let{detail:t=[]}=e;n("threads",t),this.threadMap=new Map(t.map(([e,t])=>[e,new r(t)])),t&&t.length>0&&(this.selectedTab=t[0][0],this.requestUpdate())};#t=e=>{let[t,s]=e.detail;if(n("ON RESULT",t,s),!this.threadMap.has(t))throw Error(`result doesn't contain a valid thread! ${t}`);let i=this.threadMap.get(t);if(!i)throw Error(`thread "${t} doesn't contain a valid stack! `);let a=i.peek(),r=a?a.push(s):i.push([s]);if(s.cancelled&&i.push([]),this.threadMap.set(t,i),s.next||s.node){let e=new CustomEvent("graph-active",{detail:{node:s.next??s.node,subgraphNode:s.subgraphNode},bubbles:!0,composed:!0,cancelable:!0});this.dispatchEvent(e)}this.requestUpdate(),this.updateComplete.then(()=>{let e=`#json${r-1}`,t=this.shadowRoot.querySelectorAll(e);for(let s of(n(e,t),t))s.expandAll()})};#r(e){let{id:t}=e.target;n("onSelectTab",t),this.selectedTab=t,this.requestUpdate()}#d(e){n("NEW TAB",e);let t=`Thread-${this.threadMap.size+1}`;this.threadMap.set(t,new r),this.selectedTab=t,this.requestUpdate()}#i(e){n("onNodeUpdated",e)}#a(e){n("onStateUpdated",e),"stop"===e.detail&&this.selectedTab&&this.threadMap.get(this.selectedTab)?.push([])}#n(e,t){return(0,a.html)`
    <details>
      <summary>${e.cancelled?'"CANCELLED"':e.node}</summary>
      <div class="details-content">
        <lg4j-node-output id="(${e.node})[${t}]" value="${JSON.stringify(e).trim()}"></lg4j-node-output>
      </div>
    </details>
    `}#l(){return this.selectedTab?this.threadMap.get(this.selectedTab)?.elements.filter(e=>e.length>0).map((e,t)=>(0,a.html)`
          <details class="execution" ?open="${0===t}">
            <summary>${0===t?"Last Execution":`Execution (${t})`}</summary>
            <div class="details-content">
              <table>
                <tbody>
                  ${e.map(e=>(0,a.html)`<tr><td>${this.#n(e,t)}</td></tr>`)}
                </tbody>
              </table>
            </div>
          </details>`):(0,a.html)`<div class="alert">No Data</div>`}#o(){let e=[...this.threadMap.keys()];return(0,a.html)`
    ${e.map(e=>(0,a.html)`<a id="${e}" @click="${this.#r}" role="tab" class="tab ${this.selectedTab===e?"tab-active":""}" >${e}</a>`)}
    `}render(){return(0,a.html)`
      
      <div class="result-root">
        <div role="tablist" class="tabs">
            ${this.#o()}
            <a role="tab" class="tab add-tab" @click="${this.#d}">
              <svg  xmlns="http://www.w3.org/2000/svg" width="1rem" height="1rem" viewBox="0 0 20 20">
                <circle cx="10" cy="10" r="9" fill="none" stroke="white" stroke-width="1.5"/>
                <line x1="5" y1="10" x2="15" y2="10" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
                <line x1="10" y1="5" x2="10" y2="15" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </a>
          </div>
            <div class="results-panel">
            ${this.#l()}
            </div>
        </div> 
    `}#h(e,t){return(0,a.html)`
      <details>
        <summary>${e.node}</summary>
        <div class="details-content">
        ${Object.entries(e.state).map(([e,s])=>(0,a.html)`
            <div>
                <h4 class="field-title">${e}</h4>
                <p>
                  <json-viewer id="json${t}">
                    ${JSON.stringify(s)}
                  </json-viewer>
                </p>
              </div>
          `)}
        </div>
      </details>
      `}#c(e,t){return(0,a.html)`
    <div class="card">
    <div class="card-body">
      <h2 class="card-title">${e.node}</h2>
      <details>
        <summary>${e.node}</summary>
        <div class="details-content">
        ${Object.entries(e.state).map(([e,s])=>(0,a.html)`
          <div>
              <h4 class="field-title">${e}</h4>
              <p>
                <json-viewer id="json${t}">
                ${JSON.stringify(s)}
                </json-viewer>
              </p>
            </div>
        `)}
        </div>
        </details>
    </div>
  </div>   `}}window.customElements.define("lg4j-result",l);
//# sourceMappingURL=webui.7a19c951.js.map
