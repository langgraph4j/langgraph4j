var t=globalThis,e={},a={},s=t.parcelRequire0031;null==s&&((s=function(t){if(t in e)return e[t].exports;if(t in a){var s=a[t];delete a[t];var i={id:t,exports:{}};return e[t]=i,s.call(i.exports,i,i.exports),i.exports}var r=Error("Cannot find module '"+t+"'");throw r.code="MODULE_NOT_FOUND",r}).register=function(t,e){a[t]=e},t.parcelRequire0031=s),s.register;var i=s("hNeh9"),r=s("800sp");const n=(0,s("8uVid").debug)({on:!0,topic:"LG4JExecutor"});async function*o(t){let e=t.body?.getReader(),a=new TextDecoder,s="";for(;e;){let{done:t,value:i}=await e.read();if(t)break;try{s+=a.decode(i);let t=JSON.parse(s);s="",yield t}catch(t){console.warn("JSON parse error:",t)}}}class d extends r.LitElement{static styles=[i.default,(0,r.css)`
    .container {
      display: flex;
      flex-direction: column;
      row-gap: 10px;
    }

    .commands {
      display: flex;
      flex-direction: row;
      column-gap: 10px;
    }

    .item1 {
      flex-grow: 2;
    }
    .item2 {
      flex-grow: 2;
    }
  `];static properties={url:{type:String,reflect:!0},test:{type:Boolean,reflect:!0},_executing:{state:!0}};url=null;#t;#e=null;#a;constructor(){super(),this.test=!1,this.formMetaData=[],this._executing=!1}#s(){this._executing=!0,this.dispatchEvent(new CustomEvent("state-updated",{detail:"start",bubbles:!0,composed:!0,cancelable:!0}))}#i(t){if(this._executing=!1,!t)return;if(t instanceof Error)return void this.dispatchEvent(new CustomEvent("state-updated",{detail:"error",bubbles:!0,composed:!0,cancelable:!0}));let[e,{node:a}]=t;this.dispatchEvent(new CustomEvent("state-updated",{detail:"__END__"!==a?"interrupted":"stop",bubbles:!0,composed:!0,cancelable:!0}))}#r(t){n("thread-updated",t.detail),this.#t=t.detail,this.#e=null,this.requestUpdate()}#n(t){n("onNodeUpdated",t),this.#e=t.detail,this.requestUpdate()}connectedCallback(){super.connectedCallback(),this.addEventListener("thread-updated",this.#r),this.addEventListener("node-updated",this.#n),this.#o()}disconnectedCallback(){super.disconnectedCallback(),this.removeEventListener("thread-updated",this.#r),this.removeEventListener("node-updated",this.#n)}render(){return(0,r.html)`
        <div class="container">
          ${this.formMetaData.map(({name:t,type:e})=>{switch(e){case"STRING":return(0,r.html)`<textarea id="${t}" class="textarea textarea-primary" placeholder="${t}"></textarea>`;case"IMAGE":return(0,r.html)`<lg4j-image-uploader id="${t}"></lg4j-image-uploader>`}})}
          <div class="commands">
            <button id="submit" ?disabled=${this._executing} @click="${this.#d}" class="btn btn-primary item1">Submit</button>
            <button id="resume" ?disabled=${!this.#e||this._executing} @click="${this.#l}" class="btn btn-secondary item2">
            Resume ${this.#e?"(from "+this.#e?.node+")":""}
            </button>
          </div>
        </div>
        <!--
        ==============
        ERROR DIALOG 
        ==============
        -->
        <dialog id="error_dialog" class="modal">
          <div class="modal-box">
            <form method="dialog">
              <button class="btn btn-sm btn-circle btn-ghost absolute right-2 top-2">✕</button>
            </form>
              <div class="flex items-center gap-2 mb-4 text-error">
              <svg
              xmlns="http://www.w3.org/2000/svg"
              class="h-6 w-6 shrink-0 stroke-current"
              fill="none"
              viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <p id="error_message" class="text-lg font-bold">ERROR</p>
          </div>
          </div>
        </dialog>        
        `}#c(t){let e=this.shadowRoot?.getElementById("error_dialog");if(e&&"showModal"in e){let a=e.querySelector("#error_message");a&&(a.textContent=t),e.showModal()}}async #o(){let t=await fetch(`${this.url}/init${window.location.search}`,{method:"GET",credentials:"include"});if(!t.ok)return this.#c(t.statusText),null;let e=await t.json();n("initData",e),this.dispatchEvent(new CustomEvent("init",{detail:e,bubbles:!0,composed:!0,cancelable:!0})),this.#a=e.id,this.formMetaData=e.args,this.requestUpdate()}async #l(){this.#s();let t=null;try{t=await this.#u()}catch(e){e instanceof Error&&(this.#c(e.message),t=e)}finally{this.#i(t)}}async #u(){let t=await fetch(`${this.url}/stream/${this.#a}?thread=${this.#t}&resume=true&node=${this.#e?.node}&checkpoint=${this.#e?.checkpoint}`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(this.#e?.data)});if(!t.ok)throw Error(t.statusText);this.#e=null;let e=null;for await(let a of o(t))n(a),e=a,this.dispatchEvent(new CustomEvent("result",{detail:a,bubbles:!0,composed:!0,cancelable:!0}));return e}async #d(){this.#s();let t=null;try{t=await this.#h()}catch(e){e instanceof Error&&(this.#c(e.message),t=e)}finally{this.#i(t)}}async #h(){let t=this.formMetaData.reduce((t,e)=>{let{name:a,type:s}=e,i=this.shadowRoot?.getElementById(a);switch(s){case"STRING":case"IMAGE":t[a]=i?.value}return t},{}),e=await fetch(`${this.url}/stream/${this.#a}?thread=${this.#t}`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(t)});if(!e.ok)throw Error(e.statusText);let a=null;for await(let t of o(e))n("SUBMIT RESULT",t),a=t,this.dispatchEvent(new CustomEvent("result",{detail:t,bubbles:!0,composed:!0,cancelable:!0}));return a}}window.customElements.define("lg4j-executor",d);
//# sourceMappingURL=webui.2085dc86.js.map
