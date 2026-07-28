
import { html, css, LitElement, CSSResult } from 'lit';

//import { imageToDiagram as test } from './lg4j-executor-test.js';

import { debug } from './debug.js';

const _DBG = debug( { on: true, topic: 'LG4JExecutor' } )
const _DBGW = debug( { on: true, topic: 'LG4JViewerExecutor' } )


/**
 * @file
 * @typedef {import('./types.js').ResultData} ResultData
 * @typedef {import('./types.js').EditEvent} EditEvent
 * @typedef {import('./types.js').UpdatedState} UpdatedState
 * @typedef {import('./types.js').Instance} Instance
 * @typedef {import('./types.js').ArgumentMetadata} ArgumentMetadata
 * 
 */

/**
 * Asynchronously waits for a specified number of milliseconds.
 * 
 * @param {number} ms - The number of milliseconds to wait.
 * @returns {Promise<void>} A promise that resolves after the specified delay.
 */
const delay = async (ms) => (new Promise(resolve => setTimeout(resolve, ms)));

/**
 * Asynchronously fetches data from a given fetch call and yields the data in chunks.
 * @async
 * @generator
 * @param {Response} response
 * @yields {Promise<string>} The decoded text chunk from the response stream.
 */
async function* streamingResponse(response) {
  // Attach Reader
  const reader = response.body?.getReader();

  const decoder = new TextDecoder();

  let buffer = '';
  while (true && reader) {
    // wait for next encoded chunk
    const { done, value } = await reader.read();
    // check if stream is done
    if (done) break;

    try {
      buffer += decoder.decode(value);
      const data = JSON.parse(buffer);
      buffer = '';
      yield data;
    } catch (err) {
      console.warn('JSON parse error:', err );
    }
    // Decodes data chunk and yields it
    // yield (new TextDecoder().decode(value));
  }
}

/**
 * LG4JInputElement is a custom web component that extends LitElement.
 * It provides a styled input container with a placeholder.
 * 
 * @class
 * @extends {LitElement}
 */
export class LG4JExecutorElement extends LitElement {

  /**
   * Styles applied to the component.
   * 
   * @static
   * @type {Array<CSSResult>}
   */
  static styles = [css`
    :host {
      display: block;
      color: #e5e7eb;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }

    .container {
      display: flex;
      flex-direction: column;
      row-gap: 10px;
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
      min-height: 6rem;
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
      min-height: 2.75rem;
      padding: 0.65rem 1rem;
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
      width: 1.5rem;
      height: 1.5rem;
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
  `];


  /**
   * Properties of the component.
   * 
   * @static
   * @type { import('lit').PropertyDeclarations }
   */
  static properties = {
    url: { type: String, reflect: true },
    test: { type: Boolean, reflect: true },
    _executing: { state: true }

  }

  /**
   * @type {string | null }
   */
  url = null

  /**
   * current selected thread
   * 
   * @type {string|undefined} - thread id
   */
  _selectedThread

  /**
   * current state for update 
   * 
   * @type {UpdatedState|null}
   */
  #updatedState = null
  
  /**
   * Instance id
   * 
   * @type {string|undefined} - instance id
   */
  #instanceId;

  /**
   * Creates an instance of LG4JInputElement.
   * 
   * @constructor
   */
  constructor() {
    super();
    this.test = false
    /** @type {ArgumentMetadata[]} */
    this.formMetaData = []
    this._executing = false

  }

  /**
   * if url is not set, return context path
   * 
   * @returns {string} - context path
   */
  get _contextPath() {
    // vadidate url
    const url = new URL(this.url || window.location.href);

    const pathName =  (( this.url ) ? 
      url.toString() : // if url is set, use it as is
      url.pathname).replace(/\/+$/,'')

    return pathName.replace(/\/+$/,'') // remove trailing slash
  }


  #startExecution() {

    this._executing = true
    this.dispatchEvent(new CustomEvent('state-updated', {
      detail: 'start',
      bubbles: true,
      composed: true,
      cancelable: true
    }));
  }

  /**
   * 
   * @param {[ string, UpdatedState & { next: string } ]|Error|null} result 
   */
  #stopExecution( result ) {
    this._executing = false
    
    // NO ACTION
    if( !result ) {
      return
    }

    // ON ERROR
    if( result instanceof Error ) {
      this.dispatchEvent(new CustomEvent('state-updated', {
        detail: 'error',
        bubbles: true,
        composed: true,
        cancelable: true
      }));
      return 
    }
    // ON SUCCESS
    const [ thread, { node } ] = result

    // Asuume that flow is interrupted if last node is different by last node (__END__) 
    this.dispatchEvent(new CustomEvent('state-updated', {
        detail: ( node!=='__END__' ) ? 'interrupted' : 'stop',
        bubbles: true,
        composed: true,
        cancelable: true
      }));
  }
  

  /**
   * Event handler for the 'update slected thread' event.
   * 
   * @param {CustomEvent<string>} e - The event object containing the updated data.
   */
  #onThreadUpdated(e) {
    _DBG('thread-updated', e.detail)
    this._selectedThread = e.detail
    this.#updatedState = null
    this.requestUpdate()
  }

  /**
   * 
   * @param {CustomEvent<UpdatedState>} e - The event object containing the result data.
   */
  #onNodeUpdated(e) {
    _DBG('onNodeUpdated', e)
    this.#updatedState = e.detail
    this.requestUpdate()
  }

  /**
   * Lifecycle method called when the element is added to the document's DOM.
   */
  connectedCallback() {
    super.connectedCallback();

    // @ts-ignore
    this.addEventListener("thread-updated", this.#onThreadUpdated);
    // @ts-ignore
    this.addEventListener('node-updated', this.#onNodeUpdated)

    this._callInit()

  }

  disconnectedCallback() {
    super.disconnectedCallback();

    // @ts-ignore
    this.removeEventListener("thread-updated", this.#onThreadUpdated)
    // @ts-ignore
    this.removeEventListener('node-updated', this.#onNodeUpdated)
  }


  /**
   * 
   * @param {string} detail 
   */
  #requestShowError( detail ) {

    const elem = this.shadowRoot?.getElementById('error_dialog')
    if (elem && 'showModal' in elem ) {
      const msgElem = elem.querySelector('#error_message')
      if( msgElem ) {
        msgElem.textContent = detail
      }
      //@ts-ignore
      elem.showModal()
      
      // if( timeout ) {
      //   await delay(timeout)
      //   //@ts-ignore
      //   elem.close()
      // }
   }
  
  }

  // PROTECTED METHOD
  async _callInit() {    
    const initResponse = await fetch(`${this._contextPath}/init${window.location.search}`, {
      method: 'GET',
      credentials: 'include'
    })

    if( !initResponse.ok ) {
      this.#requestShowError(initResponse.statusText) 
      return null
    }
  
    /** @type {Instance} */
    const instance = await initResponse.json()

    _DBG('initData', instance);

    this.dispatchEvent(new CustomEvent('init', {
      detail: instance,
      bubbles: true,
      composed: true,
      cancelable: true
    }));


    this.#instanceId = instance.id
    this.formMetaData = instance.args
    // this.#nodes = initData.nodes
    this.requestUpdate()
  }

  async #callResume() {

    this.#startExecution()
    let result = null

    try {

      // if (this.test) {
      //   await test.callSubmitAction(this, this.#selectedThread);
      //   return
      // }

      result =  await this.#callResumeAction()

    }
    catch (err) {
      if(err instanceof Error) {
        this.#requestShowError(err.message)
        result = err
      }
    }
    finally {
      this.#stopExecution(result)
    }

  }

  async #callResumeAction() {

    const execResponse = await fetch(`${this._contextPath}/stream/${this.#instanceId}?thread=${this._selectedThread}&resume=true&node=${this.#updatedState?.node}&checkpoint=${this.#updatedState?.checkpoint}`, {
      method: 'POST', // *GET, POST, PUT, DELETE, etc.
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify( this.#updatedState?.data )
    });

    if( !execResponse.ok ) {
      throw new Error( execResponse.statusText )
    }

    this.#updatedState = null

    /** @type [ string, UpdatedState & { next: string } ]|null */
    let lastChunk = null

    for await (let detail of streamingResponse(execResponse)) {
      _DBG( detail)
      
      lastChunk = detail

      this.dispatchEvent(new CustomEvent('result', {
        detail,
        bubbles: true,
        composed: true,
        cancelable: true
      }));
    }

    return lastChunk

  }

  async _callSubmit() {

    _DBG('callSubmit')
    
    this.#startExecution()
    let result = null

    try {

      // if (this.test) {
      //   await test.callSubmitAction(this, this.#selectedThread);
      // }

      result = await this.#callSubmitAction()
    }
    catch (err) {
      if(err instanceof Error) {
        this.#requestShowError(err.message)
        result = err
      }
    }
    finally {
        this.#stopExecution(result)

    }
  }

  /**
   * Called when the user clicks the stop button. Dispatches a 'stop' event
   * and attempts to cancel execution locally.
   */
  async #callCancel() {
    // If not executing, ignore
    if (!this._executing) return;

    const execResponse = await fetch(`${this._contextPath}/stream/${this.#instanceId}?thread=${this._selectedThread}&cancel=true`, {
      method: 'DELETE', // *GET, POST, PUT, DELETE, etc.
      credentials: 'include'
    });

    if( !execResponse.ok ) {
      throw new Error( execResponse.statusText )
    }

    /** @typedef {CustomEvent<[string,ResultData]>} */
    const event = new CustomEvent('result',{
        detail: [ this._selectedThread, { cancelled:true } ],
        bubbles: true,
        composed: true,
        cancelable: true
      })
    this.dispatchEvent( event );
  }

  async #callSubmitAction() {

    // Get input as object
    /** @type { Record<string,any> } */
    const result = {}
    /** @type { Record<string,any> } data */
    const data = this.formMetaData.reduce((acc, md) => {

      const { name, type } = md
      const elem = this.shadowRoot?.getElementById(name)

      switch (type) {
        case 'STRING':
          //@ts-ignore
          acc[name] = elem?.value
          break;
        case 'IMAGE':
          //@ts-ignore
          acc[name] = elem?.value
          break;
      }

      return acc
    }, result);

    
    const execResponse = await fetch(`${this._contextPath}/stream/${this.#instanceId}?thread=${this._selectedThread}`, {
        method: 'POST', // *GET, POST, PUT, DELETE, etc.
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
      });
  
    if( !execResponse.ok ) {
      throw new Error( execResponse.statusText )
    }

    /** @type [ string, UpdatedState & { next: string } ]|null */
    let lastChunk = null
    
    for await (let detail of streamingResponse(execResponse)) {
      _DBG( 'SUBMIT RESULT', detail)

      // lastChunk = JSON.parse(chunk);
      lastChunk = detail

      /** @typedef {CustomEvent<[string,ResultData]>} */
      const event = new CustomEvent('result', {
        detail,
        bubbles: true,
        composed: true,
        cancelable: true
      });
      this.dispatchEvent(event);
    }

    return lastChunk

  }

    /**
   * Renders the HTML template for the component.
   * 
   * @returns The rendered HTML template.
   */
  render() {

    return html`
        <div class="container">
          ${this.formMetaData.map(({ name, type }) => {
            switch (type) {
              case 'STRING':
                return html`<textarea id="${name}" placeholder="${name}"></textarea>`
              case 'IMAGE':
                return html`<lg4j-image-uploader id="${name}"></lg4j-image-uploader>`
            }
          })}
          <div class="commands">
            <button id="submit" ?disabled=${this._executing} @click="${this._callSubmit}" class="primary item1">Submit</button>
            <button id="resume" ?disabled=${!this.#updatedState || this._executing} @click="${this.#callResume}" class="secondary item2">
            Resume ${this.#updatedState ? '(from ' + this.#updatedState?.node + ')' : ''}
            </button>
            <button id="cancel" @click="${this.#callCancel}" ?disabled=${!this._executing} class="danger item3" aria-label="Stop">
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
        `;
  }

}

class LG4JViewerExecutorElement extends LG4JExecutorElement {

  static styles = [
    ...LG4JExecutorElement.styles,
    css`
      :host {
        display: none;
      }
    `
  ];

  get _contextPath() {
    return super._contextPath.concat('/viewer')
  }

  async _callInit() {    
    _DBGW('_callInit')
    const result = await super._callInit()

    setTimeout(async () => {
      this._selectedThread = 'default'
      await this._callSubmit()
    }, 1000);
  
    return result

  }

}

window.customElements.define('lg4j-executor', LG4JExecutorElement);
window.customElements.define('lg4j-viewer-executor', LG4JViewerExecutorElement);
