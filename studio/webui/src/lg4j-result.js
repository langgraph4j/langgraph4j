import { html, css, LitElement } from 'lit';
import { Stack } from './stack.js';
import { debug } from './debug.js';


const _LOG = debug( { on: true, topic: 'LG4JResult' } )
const _DBG = debug( { on: false, topic: 'LG4JResult' } )

/**
 * @file
 * @typedef {import('./types.js').NextNodeData} NextNodeData * 
 * @typedef {import('./types.js').ResultData} ResultData * 
 */

// @ts-ignore
export class LG4JResultElement extends LitElement {

  static styles = [css`
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
  `]

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

    _LOG( 'threads', threads )

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
   * @param {CustomEvent<[string, ResultData]>} e - The event object containing the result data.
   * 
   */
  #onResult = (e) => {

    const [ thread, result ] = e.detail
    _LOG( 'ON RESULT', thread, result  )
    
    if( !this.threadMap.has( thread ) ) {
      throw new Error( `result doesn't contain a valid thread! ${thread}` );
    }

    const stack = this.threadMap.get( thread )
    if( !stack ) {
      throw new Error( `thread "${thread} doesn't contain a valid stack! ` );
    }

    const results = stack.peek()

    const index = (results) ? results.push( result ) : stack.push( [result] )

    if( result.cancelled ) {
      // add new elemnt into history stack
      stack.push( [] )
    }

    this.threadMap.set( thread, stack );

    if( result.next || result.node) {

      /** @typedef {CustomEvent<NextNodeData>} */
      const event = new CustomEvent( 'graph-active', { 
        detail: { node: result.next ?? result.node, subgraphNode: result.subgraphNode },
        bubbles: true,
        composed: true,
        cancelable: true
      });

      this.dispatchEvent( event );
    }
    
    this.requestUpdate()
    
    this.updateComplete.then(() => {
      const id = `#json${index-1}`
      // @ts-ignore
      const elems = this.shadowRoot.querySelectorAll(id);
      _LOG( id, elems );
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

    _LOG( 'onSelectTab', id )

    this.selectedTab = id

    this.requestUpdate();
  }

  // @ts-ignore
  #onNewTab(event) {
    _LOG( 'NEW TAB', event)

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
    _LOG( 'onNodeUpdated', e )
  }

  /**
   * 
   * @param {CustomEvent<'start'|'stop'|'interrupted'|'error'>} e 
   */
  #onStateUpdated( e ) {
    _LOG( 'onStateUpdated', e )
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
    <details>
      <summary>${result.cancelled ? '"CANCELLED"' : result.node}</summary>
      <div class="details-content">
        <lg4j-node-output id="(${result.node})[${index}]" value="${JSON.stringify(result).trim()}"></lg4j-node-output>
      </div>
    </details>
    `
  }

  #renderResults() {
    if( !this.selectedTab ) {
      return html`<div class="alert">No Data</div>`
    }   

    return this.threadMap.get(this.selectedTab)?.elements
      .filter( results => results.length > 0 )
      .map( (results,index ) => 
        html`
          <details class="execution" ?open="${index === 0}">
            <summary>${ index === 0 ? 'Last Execution' : `Execution (${index})`}</summary>
            <div class="details-content">
              <table>
                <tbody>
                  ${results.map( result => 
                    html`<tr><td>${this.#renderResult(result, index)}</td></tr>`) }
                </tbody>
              </table>
            </div>
          </details>`)

  }
  

  #renderTabs() {

    const threads = [ ...this.threadMap.keys() ] 
    return html`
    ${threads.map( t => html`<a id="${t}" @click="${this.#onSelectTab}" role="tab" class="tab ${this.selectedTab===t ? 'tab-active' : ''}" >${t}</a>`)}
    `
  }

  render() {
  
    return html`
      
      <div class="result-root">
        <div role="tablist" class="tabs">
            ${this.#renderTabs()}
            <a role="tab" class="tab add-tab" @click="${this.#onNewTab}">
              <svg  xmlns="http://www.w3.org/2000/svg" width="1rem" height="1rem" viewBox="0 0 20 20">
                <circle cx="10" cy="10" r="9" fill="none" stroke="white" stroke-width="1.5"/>
                <line x1="5" y1="10" x2="15" y2="10" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
                <line x1="10" y1="5" x2="10" y2="15" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </a>
          </div>
            <div class="results-panel">
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
      <details>
        <summary>${result.node}</summary>
        <div class="details-content">
        ${Object.entries(result.
// @ts-ignore
        state).map(([key, value]) => html`
            <div>
                <h4 class="field-title">${key}</h4>
                <p>
                  <json-viewer id="json${index}">
                    ${JSON.stringify(value)}
                  </json-viewer>
                </p>
              </div>
          `)}
        </div>
      </details>
      `
    }
  
  // @deprecated
  // @ts-ignore
  #renderResultWithCard(result, index) {
    return html`
    <div class="card">
    <div class="card-body">
      <h2 class="card-title">${result.node}</h2>
      <details>
        <summary>${result.node}</summary>
        <div class="details-content">
        ${Object.entries(result.state).map(([key, value]) => html`
          <div>
              <h4 class="field-title">${key}</h4>
              <p>
                <json-viewer id="json${index}">
                ${JSON.stringify(value)}
                </json-viewer>
              </p>
            </div>
        `)}
        </div>
        </details>
    </div>
  </div>   `
  }

}

window.customElements.define('lg4j-result', LG4JResultElement);
