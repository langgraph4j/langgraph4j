import TWStyles from './twlit.js';

import { html, css, LitElement,nothing } from 'lit';
import { Stack } from './stack.js';
import { debug } from './debug.js';


const _DBG = debug( { on: true, topic: 'LG4JResult' } )

/**
 * @file
 * @typedef {import('./types.js').ResultData} ResultData * 
 */

// @ts-ignore
export class LG4JResultElement extends LitElement {

  static styles = [TWStyles, css`
  json-viewer {
    --font-size: .8rem;
  }`]

  static properties = {}

  /**
   * @type {Map<string, Stack<ResultData[]>>}
   */
  threadMap = new Map()
  
  /** 
   * @type {string|undefined}
   */
  #selectedThread;

  get selectedTab() {
    return this.#selectedThread
  }

  set selectedTab( thread ) {
    this.#selectedThread = thread

    this.dispatchEvent( new CustomEvent( 'thread-updated', { 
      detail: thread ,
      bubbles: true,
      composed: true,
      cancelable: true
    }));

  }

  constructor() {
    super()
  }
  
  connectedCallback() {
    super.connectedCallback();

    // @ts-ignore
    this.addEventListener( 'result', this.#onResult )
    // @ts-ignore
    this.addEventListener( 'init-threads', this.#onInitThreads )
    // @ts-ignore
    this.addEventListener( 'node-updated', this.#onNodeUpdated )
    // @ts-ignore
    this.addEventListener( 'state-updated', this.#onStateUpdated );

  }

  disconnectedCallback() {
    super.disconnectedCallback()

    // @ts-ignore
    this.removeEventListener( 'state-updated', this.#onStateUpdated );
    // @ts-ignore
    this.removeEventListener( 'result',  this.#onResult )
    // @ts-ignore
    this.removeEventListener( 'init-threads',  this.#onInitThreads )
    // @ts-ignore
    this.removeEventListener( 'node-updated', this.#onNodeUpdated )
  }

  /**
   * Event handler for the 'init threads' event.
   * 
   * @param {CustomEvent} e - The event object containing the result data.
   * 
   */
  #onInitThreads = (e) => {
    const { detail: threads  = [] } = e 

    _DBG( 'threads', threads )

    this.threadMap = new Map( threads.map( ( /** @type {[string, ResultData[]]} */ [ thread, results ] ) => 
      [ thread, new Stack( results ) ]
    ))
    
    if( threads && threads.length > 0 ) {
      this.selectedTab = threads[0][0]
      this.requestUpdate()  
    }
  }

  /**
   * Event handler for the 'result' event.
   * 
   * @param {CustomEvent} e - The event object containing the result data.
   * 
   */
  #onResult = (e) => {

    const [ thread, result ] = e.detail
    _DBG( 'ON RESULT', thread, result  )
    
    if( !this.threadMap.has( thread ) ) {
      throw new Error( `result doesn't contain a valid thread! ${thread}` );
    }

    const stack = this.threadMap.get( thread )
    if( !stack ) {
      throw new Error( `thread "${thread} doesn't contain a valid stack! ` );
    }

    const results = stack.peek()

    const index = (results) ? results.push( result ) : stack.push( [result] )
    
    this.threadMap.set( thread, stack );

    if( result.next ) {
      this.dispatchEvent( new CustomEvent( 'graph-active', { 
        detail: result.next,
        bubbles: true,
        composed: true,
        cancelable: true
      }));  
    }
    
    this.requestUpdate()
    
    this.updateComplete.then(() => {
      const id = `#json${index-1}`
      // @ts-ignore
      const elems = this.shadowRoot.querySelectorAll(id);
      _DBG( id, elems );
      for (const elem of elems) {
        // @ts-ignore
        elem.expandAll()
      }
    });
  }

  /**
   * Event handler select tab.
   * 
   * @param {Event} event - The event object.
   * 
   */
  #onSelectTab( event ) {
    // @ts-ignore
    const { id } = event.target

    _DBG( 'onSelectTab', id )

    this.selectedTab = id

    this.requestUpdate();
  }

  // @ts-ignore
  #onNewTab(event) {
    _DBG( 'NEW TAB', event)

    const threadId = `Thread-${this.threadMap.size+1}`

    this.threadMap.set( threadId, new Stack() );

    this.selectedTab = threadId

    this.requestUpdate();

  }

  /**
   * 
   * @param {CustomEvent<ResultData>} e - The event object containing the result data.
   * 
   */
  #onNodeUpdated( e ) {
    _DBG( 'onNodeUpdated', e )
  }

  /**
   * 
   * @param {CustomEvent<'start'|'stop'|'interrupted'|'error'>} e 
   */
  #onStateUpdated( e ) {
    _DBG( 'onStateUpdated', e )
    if( e.detail === 'stop' && this.selectedTab ) { 

      // add new elemnt into history stack
      const stack = this.threadMap.get( this.selectedTab )?.push( [] )

    }
  }

  /** 
   * Renders a result.
   * @param {ResultData} result - The result data to render.
   * @returns The template for the result.
   */
  // @ts-ignore
  #renderResult(result, index) {

    return html`
    <div class="collapse collapse-arrow bg-base-200">
      <input type="radio" name="item-${index}" checked="checked" />
      <div class="collapse-title text-ml font-bold">${result.node}</div>
      <div class="collapse-content">
        <lg4j-node-output>${JSON.stringify(result).trim()}</log4j-node-output>  
      </div>
    </div>
    `
  }

  #renderResults() {
    if( !this.selectedTab ) {
      return html`<div class="alert alert-warning">No Data</div>`
    }   

    return this.threadMap.get(this.selectedTab)?.elements
      .filter( results => results.length > 0 )
      .map( (results,index ) => 
        html`
          <div class="collapse collapse-plus bg-neutral-500">
            <input type="radio" name="execution-${ index === 0 ? '0' : '1'}" checked="${ index === 0 ? 'checked' : nothing }" />
            <div class="collapse-title text-ml font-bold">${ index === 0 ? 'Last Execution' : `Execution (${index})`}</div>
            <div class="collapse-content">
              <table class="table table-pin-rows">
                <tbody>
                  ${results.map( (result) => 
                    html`<tr><td>${this.#renderResult(result, index)}</td></tr>`) }
                </tbody>
              </table>
            </div>
          </div>`)

  }
  

  #renderTabs() {

    const threads = [ ...this.threadMap.keys() ] 
    return html`
    ${threads.map( t => html`<a id="${t}" @click="${this.#onSelectTab}" role="tab" class="tab ${this.selectedTab===t ? 'tab-active' : ''}" >${t}</a>`)}
    `
  }

  render() {
  
    return html`
      
      <div class="h-full">
        <div role="tablist" class="tabs tabs-bordered">
            ${this.#renderTabs()}
            <a role="tab" class="tab" @click="${this.#onNewTab}">
              <svg  xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20">
                <circle cx="10" cy="10" r="9" fill="none" stroke="white" stroke-width="1.5"/>
                <line x1="5" y1="10" x2="15" y2="10" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
                <line x1="10" y1="5" x2="10" y2="15" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </a>
          </div>
            <div class="max-h-[95%] overflow-x-auto bg-slate-500">
            ${ this.#renderResults() }
            </div>
        </div> 
    `;
  }

  /** 
   * Renders a result.
   * @param {ResultData} result - The result data to render.
   * @returns The template for the result.
   * @deprecated
   */
    // @ts-ignore
    #renderResultDeprecated(result, index) {

      return html`
      <div class="collapse collapse-arrow bg-base-200">
        <input type="radio" name="item-1" checked="checked" />
        <div class="collapse-title text-ml font-bold">${result.node}</div>
        <div class="collapse-content">
        ${Object.entries(result.
// @ts-ignore
        state).map(([key, value]) => html`
            <div>
                <h4 class="italic">${key}</h4>
                <p class="my-3">
                  <json-viewer id="json${index}">
                    ${JSON.stringify(value)}
                  </json-viewer>
                </p>
              </div>
          `)}
        </div>
      </div>
      `
    }
  
  // @deprecated
  // @ts-ignore
  #renderResultWithCard(result, index) {
    return html`
    <div class="card bg-neutral text-neutral-content">
    <div class="card-body">
      <h2 class="card-title">${result.node}</h2>
      <div class="collapse collapse-arrow bg-base-200">
        <input type="radio" name="item-1" checked="checked" />
        <div class="collapse-content">
        ${Object.entries(result.state).map(([key, value]) => html`
          <div>
              <h4 class="italic">${key}</h4>
              <p class="my-3">
                <json-viewer id="json${index}">
                ${JSON.stringify(value)}
                </json-viewer>
              </p>
            </div>
        `)}
        </div>
        </div>
    </div>
  </div>   `
  }

}

window.customElements.define('lg4j-result', LG4JResultElement);
