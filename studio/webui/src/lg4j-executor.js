
import TWStyles from './twlit.js';

import { html, css, LitElement, CSSResult } from 'lit';

//import { imageToDiagram as test } from './lg4j-executor-test.js';

import { debug } from './debug.js';

const _DBG = debug( { on: true, topic: 'LG4JExecutor' } )


/**
 * @file
 * @typedef {import('./types.js').ResultData} ResultData * 
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
  static styles = [TWStyles, css`
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
  #selectedThread

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
    this.#selectedThread = e.detail
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

    // if (this.test) {
    //   test.callInit(this)
    //     .then(instance => {
    //       this.formMetaData = instance.args 
    //       this.requestUpdate()
    //     })
    //   return
    // }

    this.#callInit()

  }

  disconnectedCallback() {
    super.disconnectedCallback();

    // @ts-ignore
    this.removeEventListener("thread-updated", this.#onThreadUpdated)
    // @ts-ignore
    this.removeEventListener('node-updated', this.#onNodeUpdated)
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
                return html`<textarea id="${name}" class="textarea textarea-primary" placeholder="${name}"></textarea>`
              case 'IMAGE':
                return html`<lg4j-image-uploader id="${name}"></lg4j-image-uploader>`
            }
          })}
          <div class="commands">
            <button id="submit" ?disabled=${this._executing} @click="${this.#callSubmit}" class="btn btn-primary item1">Submit</button>
            <button id="resume" ?disabled=${!this.#updatedState || this._executing} @click="${this.#callResume}" class="btn btn-secondary item2">
            Resume ${this.#updatedState ? '(from ' + this.#updatedState?.node + ')' : ''}
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
        `;
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

  async #callInit() {
  
    const initResponse = await fetch(`${this.url}/init${window.location.search}`, {
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

    const execResponse = await fetch(`${this.url}/stream/${this.#instanceId}?thread=${this.#selectedThread}&resume=true&node=${this.#updatedState?.node}&checkpoint=${this.#updatedState?.checkpoint}`, {
      method: 'POST', // *GET, POST, PUT, DELETE, etc.
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

  async #callSubmit() {

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

    
    const execResponse = await fetch(`${this.url}/stream/${this.#instanceId}?thread=${this.#selectedThread}`, {
        method: 'POST', // *GET, POST, PUT, DELETE, etc.
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

      this.dispatchEvent(new CustomEvent('result', {
        detail,
        bubbles: true,
        composed: true,
        cancelable: true
      }));

    }
    
    return lastChunk

  }

}
window.customElements.define('lg4j-executor', LG4JExecutorElement);
