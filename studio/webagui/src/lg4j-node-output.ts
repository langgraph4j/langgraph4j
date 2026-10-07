import React from 'react'
import ReactDOM from 'react-dom/client'; 
import ReactJson from '@microlink/react-json-view'
import { debug } from './debug';
import type { EditEvent, ResultData, UpdatedState } from './types';

const _DBG = debug( { on: true, topic: 'LG4JNodeOutput' } )

export class LG4JNodeOutput extends HTMLElement {
    
  static get observedAttributes() {
      return ['value'];
  }

  declare root: ReactDOM.Root

  constructor() {
      super()

      const shadowRoot = this.attachShadow({ mode: 'open' });
      
      const style = document.createElement("style");
      style.textContent = `
      <style>
      </style>
      `
      
      shadowRoot.appendChild(style);

  }

  /**
   * @param {string} name
   * @param {any} oldValue
   * @param {any} newValue
   */
  attributeChangedCallback(name:string, oldValue:any, newValue:any) {
      if (name === 'value') {
        if (newValue !== null && newValue !== oldValue) {
          _DBG( 'attributeChangedCallback.value', newValue )

          this.root = this.#createRoot( JSON.parse(newValue) )
        }
      }
  }

  connectedCallback() {

      // const value = this.textContent ?? '{}'
      
      // _DBG( 'value', value )

      // this.root = this.#createRoot( JSON.parse(value) )
      
  }

  disconnectedCallback() {

    _DBG( 'disconnectedCallback' )
    this.root?.unmount()

  }

  get isCollapsed() {
    return this.getAttribute('collapsed') === 'true'
  }

  /**
   * 
   * @param {EditEvent} e
   * @param {ResultData} result
   */
  #onEdit( e:EditEvent, result:ResultData ) {

    if( result.checkpoint ) {

      /**
       * @type {UpdatedState}
       */
      const detail:UpdatedState = {
        node: result.node,
        checkpoint: result.checkpoint,
        data: e.updated_src
      }

      this.dispatchEvent( new CustomEvent( 'node-updated', { 
        detail,
        bubbles: true,
        composed: true,
        cancelable: true
      }));
      
      return true;
    }

    return false;
  }



  /**
   * 
   * @param {ResultData} value 
   * @returns 
   */
  #createRoot( value:ResultData ) {

    const mountPointId = `json-view-${this.id}`;
    
    // FIX #241
    this?.shadowRoot?.getElementById( mountPointId )?.remove()

    const mountPoint = document.createElement('span');
    mountPoint.setAttribute( 'id', mountPointId )
    this.shadowRoot?.appendChild(mountPoint);

    const root = ReactDOM.createRoot(mountPoint);

    // @ts-ignore
    const component = React.createElement( ReactJson, { 
      src: value.state,
      enableClipboard: false,
      displayDataTypes: false,
      name: false,
      collapsed: this.isCollapsed,
      theme: 'monokai',
      onEdit: (e:any) => this.#onEdit(e, value ),
      validationMessage: 'Read only'

    } )
    
    root.render( component )
    
    return root
  }
}


window.customElements.define('lg4j-node-output', LG4JNodeOutput);