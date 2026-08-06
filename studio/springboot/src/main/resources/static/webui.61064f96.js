var e=globalThis,t={},a={},r=e.parcelRequire0031;null==r&&((r=function(e){if(e in t)return t[e].exports;if(e in a){var r=a[e];delete a[e];var i={id:e,exports:{}};return t[e]=i,r.call(i.exports,i,i.exports),i.exports}var s=Error("Cannot find module '"+e+"'");throw s.code="MODULE_NOT_FOUND",s}).register=function(e,t){a[e]=t},e.parcelRequire0031=r),r.register;var i=r("800sp"),s=r("8uVid");let n=(0,s.debug)({on:!0,topic:"LG4JExecutor"}),o=(0,s.debug)({on:!0,topic:"LG4JViewerExecutor"});async function*d(e){let t=e.body?.getReader(),a=new TextDecoder,r="";for(;t;){let{done:e,value:i}=await t.read();if(e)break;try{r+=a.decode(i);let e=JSON.parse(r);r="",yield e}catch(e){console.warn("JSON parse error:",e)}}}class l extends Error{constructor(e){super(e.statusText||"Retrieve data error")}}class c extends i.LitElement{static styles=[(0,i.css)`
    :host {
      display: block;
      color: #e5e7eb;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
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
      transition: background 0.15s ease, border-color 0.15s ease, opacity 0.15s ease;
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
  `];static properties={url:{type:String,reflect:!0},test:{type:Boolean,reflect:!0},_executing:{state:!0}};url=null;_selectedThread;#e=null;#t;constructor(){super(),this.test=!1,this.formMetaData=[],this._executing=!1}get _contextPath(){let e=new URL(this.url||window.location.href);return(this.url?e.toString():e.pathname).replace(/\/+$/,"").replace(/\/+$/,"")}#a(){this._executing=!0,this.dispatchEvent(new CustomEvent("state-updated",{detail:"start",bubbles:!0,composed:!0,cancelable:!0}))}#r(e){if(this._executing=!1,!e)return;if(e instanceof Error)return void this.dispatchEvent(new CustomEvent("state-updated",{detail:"error",bubbles:!0,composed:!0,cancelable:!0}));let[t,{node:a}]=e;this.dispatchEvent(new CustomEvent("state-updated",{detail:"__END__"!==a?"interrupted":"stop",bubbles:!0,composed:!0,cancelable:!0}))}#i(e){n("thread-updated",e.detail),this._selectedThread=e.detail,this.#e=null,this.requestUpdate()}#s(e){n("onNodeUpdated",e),this.#e=e.detail,this.requestUpdate()}connectedCallback(){super.connectedCallback(),this.addEventListener("thread-updated",this.#i),this.addEventListener("node-updated",this.#s),this._callInit()}disconnectedCallback(){super.disconnectedCallback(),this.removeEventListener("thread-updated",this.#i),this.removeEventListener("node-updated",this.#s)}#n(e){let t=this.shadowRoot?.getElementById("error_dialog");if(t&&"showModal"in t){let a=t.querySelector("#error_message");a&&(a.textContent=e),t.showModal()}}async _callInit(){let e=await fetch(`${this._contextPath}/init${window.location.search}`,{method:"GET",credentials:"include"});if(!e.ok)return this.#n(e.statusText),null;let t=await e.json();n("initData",t),this.dispatchEvent(new CustomEvent("init",{detail:t,bubbles:!0,composed:!0,cancelable:!0})),this.#t=t.id,this.formMetaData=t.args,this.requestUpdate()}async #o(){this.#a();let e=null;try{e=await this.#d()}catch(t){t instanceof Error&&(this.#n(t.message),e=t)}finally{this.#r(e)}}async #d(){let e=await fetch(`${this._contextPath}/stream/${this.#t}?thread=${this._selectedThread}&resume=true&node=${this.#e?.node}&checkpoint=${this.#e?.checkpoint}`,{method:"POST",credentials:"include",headers:{"Content-Type":"application/json"},body:JSON.stringify(this.#e?.data)});if(!e.ok)throw new l(e);this.#e=null;let t=null;for await(let a of d(e))n(a),t=a,this.dispatchEvent(new CustomEvent("result",{detail:a,bubbles:!0,composed:!0,cancelable:!0}));return t}async _callSubmit(){n("callSubmit"),this.#a();let e=null;try{e=await this.#l()}catch(t){t instanceof l&&(this.#n(t.message),e=t)}finally{this.#r(e)}}async #c(){if(!this._executing)return;let e=await fetch(`${this._contextPath}/stream/${this.#t}?thread=${this._selectedThread}&cancel=true`,{method:"DELETE",credentials:"include"});if(!e.ok)throw new l(e);let t=new CustomEvent("result",{detail:[this._selectedThread,{cancelled:!0}],bubbles:!0,composed:!0,cancelable:!0});this.dispatchEvent(t)}async #l(){let e=this.formMetaData.reduce((e,t)=>{let{name:a,type:r}=t,i=this.shadowRoot?.getElementById(a);switch(r){case"STRING":case"IMAGE":e[a]=i?.value}return e},{}),t=await fetch(`${this._contextPath}/stream/${this.#t}?thread=${this._selectedThread}`,{method:"POST",credentials:"include",headers:{"Content-Type":"application/json"},body:JSON.stringify(e)});if(!t.ok)throw new l(t);let a=null;for await(let e of d(t)){n("SUBMIT RESULT",e),a=e;let t=new CustomEvent("result",{detail:e,bubbles:!0,composed:!0,cancelable:!0});this.dispatchEvent(t)}return a}render(){return(0,i.html)`
        <div class="container">
          ${this.formMetaData.map(({name:e,type:t})=>{switch(t){case"STRING":return(0,i.html)`<textarea id="${e}" placeholder="${e}"></textarea>`;case"IMAGE":return(0,i.html)`<lg4j-image-uploader id="${e}"></lg4j-image-uploader>`}})}
          <div class="commands">
            <button id="submit" ?disabled=${this._executing} @click="${this._callSubmit}" class="primary item1">Submit</button>
            <button id="resume" ?disabled=${!this.#e||this._executing} @click="${this.#o}" class="secondary item2">
            Resume ${this.#e?"(from "+this.#e?.node+")":""}
            </button>
            <button id="cancel" @click="${this.#c}" ?disabled=${!this._executing} class="danger item3" aria-label="Stop">
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
        `}}class u extends c{static styles=[...c.styles,(0,i.css)`
      .container {
        display: none;
      }
    `];connectedCallback(){this.hidden=!0,this.setAttribute("aria-hidden","true"),super.connectedCallback()}get _contextPath(){return super._contextPath.concat("/viewer")}async _callInit(){o("_callInit");let e=await super._callInit();return setTimeout(async()=>{this._selectedThread="default",await this._callSubmit()},1e3),e}}window.customElements.define("lg4j-executor",c),window.customElements.define("lg4j-viewer-executor",u);
//# sourceMappingURL=webui.61064f96.js.map
