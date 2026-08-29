import { html, css, LitElement } from 'lit';
import { debug } from './debug.js';

/**
 * @file
 * @typedef {import('./types.js').Instance} Instance
 * @typedef {import('./types.js').NextNodeData} NextNodeData
 */


const _LOG = debug( { on: true, topic: 'LG4JWorkbench' } )


export class LG4JWorkbenchElement extends LitElement {

  static styles = [css`
    :host {
      display: block;
      min-height: 100vh;
      color: #e5e7eb;
      background: #0f172a;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }

    .shell {
      height: 100vh;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .navbar {
      display: flex;
      align-items: center;
      gap: 1rem;
      min-height: 1rem;
      height: 2.1rem;
      padding: 0 1rem;
      background: #111827;
      border-bottom: 1px solid #1f2937;
      flex-shrink: 0;
    }

    .title {
      display: inline-flex;
      align-items: center;
      min-height: 3rem;
      padding: 0 0.5rem;
      color: #f9fafb;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-weight: 700;
      text-decoration: none;
    }

    .status {
      display: flex;
      align-items: center;
      flex: 1;
      min-width: 0;
      margin-left: 2.5rem;
    }

    #message {
      margin-left: 1rem;
      font-style: italic;
      color: #cbd5e1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .toggle-results {
      width: 1.5rem;
      height: 1.5rem;
      padding: 0;
      display: inline-grid;
      place-items: center;
      flex-shrink: 0;
      border: 1px solid #374151;
      border-radius: 0.375rem;
      color: #e5e7eb;
      background: #1f2937;
      cursor: pointer;
      line-height: 0;
    }

    .toggle-results:hover {
      color: #ffffff;
      background: #273548;
      border-color: #4b5563;
    }

    .toggle-results:focus-visible {
      outline: 2px solid #60a5fa;
      outline-offset: 2px;
    }

    .toggle-results svg {
      width: 1rem;
      height: 1rem;
      display: block;
    }

    .hidden {
      display: none;
    }

    .spinner {
      width: 1rem;
      height: 1rem;
      border: 3px solid rgba(96, 165, 250, 0.25);
      border-top-color: #60a5fa;
      border-radius: 999px;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }

    .layout {
      flex: 1;
      min-height: 0;
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      column-gap: 0.5rem;
      padding: 0.5rem;
    }

    .layout.results-hidden {
      grid-template-columns: minmax(0, 1fr);
    }

    .left-column {
      min-height: 0;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .graph-panel {
      flex: 1;
      min-height: 0;
      display: flex;
      border: 1px solid #d1d5db;
      overflow: hidden;
    }

    slot[name="graph"] {
      display: block;
      flex: 1;
      min-width: 0;
      min-height: 0;
    }

    ::slotted([slot="graph"]) {
      display: block;
      width: 100%;
      height: 100%;
      min-width: 0;
      min-height: 0;
    }

    .result-panel {
      min-height: 0;
      overflow: hidden;
    }

    .result-panel[hidden] {
      display: none;
    }

    .executor-panel {
      flex-shrink: 0;
      min-height: 0;
      overflow: auto;
    }
  `];

  static properties = {
    title: {},
    resultPanelVisible: { state: true },
  }

  constructor() {
    super();
    this.resultPanelVisible = true;
  }

  #toggleResultPanel() {
    this.resultPanelVisible = !this.resultPanelVisible;
  }

  /**
   * @param {CustomEvent} e 
   * @param {string} [slot]
   */
  #routeEvent( e, slot ) {
    
    const { type, detail } = e
    
    if( !slot ) {
      slot = type.split('-')[0]
    }

    _LOG( 'routeEvent', type, slot )
    
    const event = new CustomEvent( type, { detail } );

    const elem = this.querySelector(`[slot="${slot}"]`)
    if( !elem ) {
      console.error( `slot '${slot}' not found!` )
      return
    }
    elem.dispatchEvent( event )

  }

  /**
   * Event handler for the 'init' event.
   * 
   * @param {CustomEvent<Instance>} e - The event object containing init data.
   */
  #routeInitEvent( e ) {
      const { graph, title, threads  } = e.detail 

      this.#routeEvent( new CustomEvent( 'graph', { detail: graph }));
      this.#routeEvent( new CustomEvent( 'init-threads', { detail: threads }), 'result');

      if( title ) {
        this.title = title
        this.requestUpdate()
      }
  }

  /**
   * Event handler for the 'updates' event.
   * 
   * @param {CustomEvent} e - The event object containing the updated data.
   */
  #routeUpdateEvent( e ) {
    _LOG( 'got updated event', e );
    this.#routeEvent( new CustomEvent( `${e.type}`, { detail: e.detail }), 'executor');
  }

  /**
   * 
   * @param {string} message 
   */
  #writeMessage( message ) {
    const elem = this.shadowRoot?.getElementById('message')
    if( elem ) {
      elem.textContent = message
    }
  }

  /**
   * 
   * @param {CustomEvent<NextNodeData>} e 
   */
  #onGraphActive( e ) {
    this.#writeMessage( e.detail.node )
    this.#routeEvent( e )
  }

  /**
   * 
   * @param {CustomEvent<'start'|'stop'|'interrupted'|'error'>} e 
   */
  #onStateUpdated( e ) {
    const elem = this.shadowRoot?.getElementById('spinner')
    if( elem ) {

      if( e.detail === 'start' ) {
        elem.classList.remove('hidden')
        return 
      }

      elem.classList.add('hidden')

      if( e.detail === 'interrupted' ) {
        this.#writeMessage( 'INTERRUPTED' )
      }
      
    }
    this.#routeEvent( e , 'result')
    this.#routeEvent( e , 'graph')
  }

  connectedCallback() {
    super.connectedCallback()

    // @ts-ignore
    this.addEventListener( 'init', this.#routeInitEvent );
    // @ts-ignore
    this.addEventListener( 'result', this.#routeEvent );
    // @ts-ignore
    this.addEventListener( 'graph-active', this.#onGraphActive);
    // @ts-ignore
    this.addEventListener( 'thread-updated', this.#routeUpdateEvent );
    // @ts-ignore
    this.addEventListener( 'node-updated', this.#routeUpdateEvent )
    // @ts-ignore
    this.addEventListener( 'state-updated', this.#onStateUpdated );

  }

  disconnectedCallback() {
    super.disconnectedCallback()

    // @ts-ignore
    this.removeEventListener( 'state-updated', this.#onStateUpdated );
    // @ts-ignore
    this.removeEventListener( 'node-updated', this.#routeUpdateEvent )
    // @ts-ignore
    this.removeEventListener( 'thread-updated', this.#routeUpdateEvent );
    // @ts-ignore
    this.removeEventListener( 'graph-active', this.#onGraphActive );
    // @ts-ignore
    this.removeEventListener( 'result', this.#routeEvent );
    // @ts-ignore
    this.removeEventListener( 'init', this.#routeInitEvent );

  }

  // firstUpdated() {
  // }
  
  render() {
    const resultToggleLabel = this.resultPanelVisible ? 'Hide result panel' : 'Show result panel';

    return html`
<div class="shell">

  <div class="navbar">

    <div>
      <a class="title">${this.title}</a>
    </div>

    <div class="status">
      <span id="spinner" class="hidden spinner"></span>
      <span id="message"></span>
    </div>

    <button
      class="toggle-results"
      type="button"
      title="${resultToggleLabel}"
      aria-label="${resultToggleLabel}"
      aria-expanded="${this.resultPanelVisible}"
      @click="${this.#toggleResultPanel}">
      <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path d="M4 7h16M4 12h16M4 17h16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"></path>
      </svg>
    </button>

</div>

  <div class="layout ${this.resultPanelVisible ? '' : 'results-hidden'}">    
    <div class="left-column">
      <div class="graph-panel"><slot name="graph">LEFT</slot></div>
      <div class="executor-panel"><slot name="executor">BOTTOM</slot></div>
    </div>
    <div class="result-panel" ?hidden="${!this.resultPanelVisible}"><slot name="result">RIGHT</slot></div>
  </div>
</div>
    `;
  }
}

window.customElements.define('lg4j-workbench', LG4JWorkbenchElement);
