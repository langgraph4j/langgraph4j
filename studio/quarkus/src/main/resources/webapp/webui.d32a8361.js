var e=globalThis,t={},s={},a=e.parcelRequire0031;null==a&&((a=function(e){if(e in t)return t[e].exports;if(e in s){var a=s[e];delete s[e];var i={id:e,exports:{}};return t[e]=i,a.call(i.exports,i,i.exports),i.exports}var d=Error("Cannot find module '"+e+"'");throw d.code="MODULE_NOT_FOUND",d}).register=function(e,t){s[e]=t},e.parcelRequire0031=a),a.register;var i=a("hNeh9"),d=a("800sp");class l{items;constructor(e=[]){this.items=e}push(e){return this.items.push(e)}pop(){return this.items.pop()}peek(){return this.items.at(-1)}get elements(){return this.items.toReversed()}isEmpty(){return 0===this.items.length}get size(){return this.items.length}clear(){this.items=[]}}const r=(0,a("8uVid").debug)({on:!0,topic:"LG4JResult"});class n extends d.LitElement{static styles=[i.default,(0,d.css)`
  json-viewer {
    --font-size: .8rem;
  }`];static properties={};threadMap=new Map;#e;get selectedTab(){return this.#e}set selectedTab(e){this.#e=e,this.dispatchEvent(new CustomEvent("thread-updated",{detail:e,bubbles:!0,composed:!0,cancelable:!0}))}constructor(){super()}connectedCallback(){super.connectedCallback(),this.addEventListener("result",this.#t),this.addEventListener("init-threads",this.#s),this.addEventListener("node-updated",this.#a),this.addEventListener("state-updated",this.#i)}disconnectedCallback(){super.disconnectedCallback(),this.removeEventListener("state-updated",this.#i),this.removeEventListener("result",this.#t),this.removeEventListener("init-threads",this.#s),this.removeEventListener("node-updated",this.#a)}#s=e=>{let{detail:t=[]}=e;r("threads",t),this.threadMap=new Map(t.map(([e,t])=>[e,new l(t)])),t&&t.length>0&&(this.selectedTab=t[0][0],this.requestUpdate())};#t=e=>{let[t,s]=e.detail;if(r("ON RESULT",t,s),!this.threadMap.has(t))throw Error(`result doesn't contain a valid thread! ${t}`);let a=this.threadMap.get(t);if(!a)throw Error(`thread "${t} doesn't contain a valid stack! `);let i=a.peek(),d=i?i.push(s):a.push([s]);this.threadMap.set(t,a),s.next&&this.dispatchEvent(new CustomEvent("graph-active",{detail:s.next,bubbles:!0,composed:!0,cancelable:!0})),this.requestUpdate(),this.updateComplete.then(()=>{let e=`#json${d-1}`,t=this.shadowRoot.querySelectorAll(e);for(let s of(r(e,t),t))s.expandAll()})};#d(e){let{id:t}=e.target;r("onSelectTab",t),this.selectedTab=t,this.requestUpdate()}#l(e){r("NEW TAB",e);let t=`Thread-${this.threadMap.size+1}`;this.threadMap.set(t,new l),this.selectedTab=t,this.requestUpdate()}#a(e){r("onNodeUpdated",e)}#i(e){r("onStateUpdated",e),"stop"===e.detail&&this.selectedTab&&this.threadMap.get(this.selectedTab)?.push([])}#r(e,t){return(0,d.html)`
    <div class="collapse collapse-arrow bg-base-200">
      <input type="radio" name="item-${t}" checked="checked" />
      <div class="collapse-title text-ml font-bold">${e.node}</div>
      <div class="collapse-content">
        <lg4j-node-output>${JSON.stringify(e).trim()}</log4j-node-output>  
      </div>
    </div>
    `}#n(){return this.selectedTab?this.threadMap.get(this.selectedTab)?.elements.filter(e=>e.length>0).map((e,t)=>(0,d.html)`
          <div class="collapse collapse-plus bg-neutral-500">
            <input type="radio" name="execution-${0===t?"0":"1"}" checked="${0===t?"checked":d.nothing}" />
            <div class="collapse-title text-ml font-bold">${0===t?"Last Execution":`Execution (${t})`}</div>
            <div class="collapse-content">
              <table class="table table-pin-rows">
                <tbody>
                  ${e.map(e=>(0,d.html)`<tr><td>${this.#r(e,t)}</td></tr>`)}
                </tbody>
              </table>
            </div>
          </div>`):(0,d.html)`<div class="alert alert-warning">No Data</div>`}#o(){let e=[...this.threadMap.keys()];return(0,d.html)`
    ${e.map(e=>(0,d.html)`<a id="${e}" @click="${this.#d}" role="tab" class="tab ${this.selectedTab===e?"tab-active":""}" >${e}</a>`)}
    `}render(){return(0,d.html)`
      
      <div class="h-full">
        <div role="tablist" class="tabs tabs-bordered">
            ${this.#o()}
            <a role="tab" class="tab" @click="${this.#l}">
              <svg  xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20">
                <circle cx="10" cy="10" r="9" fill="none" stroke="white" stroke-width="1.5"/>
                <line x1="5" y1="10" x2="15" y2="10" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
                <line x1="10" y1="5" x2="10" y2="15" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </a>
          </div>
            <div class="max-h-[95%] overflow-x-auto bg-slate-500">
            ${this.#n()}
            </div>
        </div> 
    `}#c(e,t){return(0,d.html)`
      <div class="collapse collapse-arrow bg-base-200">
        <input type="radio" name="item-1" checked="checked" />
        <div class="collapse-title text-ml font-bold">${e.node}</div>
        <div class="collapse-content">
        ${Object.entries(e.state).map(([e,s])=>(0,d.html)`
            <div>
                <h4 class="italic">${e}</h4>
                <p class="my-3">
                  <json-viewer id="json${t}">
                    ${JSON.stringify(s)}
                  </json-viewer>
                </p>
              </div>
          `)}
        </div>
      </div>
      `}#h(e,t){return(0,d.html)`
    <div class="card bg-neutral text-neutral-content">
    <div class="card-body">
      <h2 class="card-title">${e.node}</h2>
      <div class="collapse collapse-arrow bg-base-200">
        <input type="radio" name="item-1" checked="checked" />
        <div class="collapse-content">
        ${Object.entries(e.state).map(([e,s])=>(0,d.html)`
          <div>
              <h4 class="italic">${e}</h4>
              <p class="my-3">
                <json-viewer id="json${t}">
                ${JSON.stringify(s)}
                </json-viewer>
              </p>
            </div>
        `)}
        </div>
        </div>
    </div>
  </div>   `}}window.customElements.define("lg4j-result",n);
//# sourceMappingURL=webui.d32a8361.js.map
