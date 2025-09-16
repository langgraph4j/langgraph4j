import TWStyles from './twlit.js';
import { html, css, LitElement } from 'lit';
import { debug } from './debug.js';

/**
 * @file
 * @typedef {import('./types.js').Instance} Instance
 */


const _DBG = debug( { on: true, topic: 'LG4JWorkbench' } )


export class LG4JWorkbenchElement extends LitElement {

  static styles = [css``, TWStyles];

  static properties = {
    title: {},
  }

  constructor() {
    super();
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

    _DBG( 'routeEvent', type, slot )
    
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
    _DBG( 'got updated event', e );
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
   * @param {CustomEvent<string>} e 
   */
  #onGraphActive( e ) {
    this.#writeMessage( e.detail )
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
    return html`
<div class="h-screen">

  <div class="navbar bg-base-100">

    <div class="flex-none">
      <a class="btn btn-ghost text-xl">${this.title}</a>
    </div>

    <div class="flex-1 ml-10">
      <span id="spinner" class="hidden loading loading-spinner text-primary"></span>
      <span id="message" class="ml-4 italic"></span>
    </div>


</div>

  <div class="h-full grid gap-x-2 gap-y-9 grid-cols-2 grid-rows-5 ">    
    <div class="row-span-3 border border-gray-300"><slot name="graph">LEFT</slot></div>
    <div class="row-span-5"><slot name="result">RIGHT</slot></div>
    <div class=" border-slate-50"><slot name="executor">BOTTOM</slot></div>
  </div>
</div>
    `;
  }
}

window.customElements.define('lg4j-workbench', LG4JWorkbenchElement);
