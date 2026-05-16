import mermaid from 'mermaid';
//const mermaidAPI = mermaid.mermaidAPI;
import * as d3 from 'd3'
import { debug } from './debug.js';


/**
 * @file
 * @typedef {import('./types.js').NextNodeData} NextNodeData * 
 */

const _LOG = debug( { on: false, topic: 'LG4jGraph' } )
const _DBG = debug( { on: false, topic: 'LG4jGraph' } )

/**
 * Mermaid Component
 * @class
 */
export class LG4jMermaid extends HTMLElement {


  constructor() {
    super();

    
    mermaid.initialize({
      logLevel: 'none',
      startOnLoad: false,
      theme: 'dark',
      flowchart: {
        useMaxWidth: false
      }
    });

    this._content = null
    this._activeClass = null
    // @ts-ignore
    this._lastTransform = null


    const shadowRoot = this.attachShadow({ mode: 'open' });

    const style = document.createElement("style");
    style.textContent = `
    :host {
      display: block;
      width: 100%;
      height: 100%;
    }

    .mermaid {
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #171717;
    }
    `
    shadowRoot.appendChild(style);

    const container = document.createElement('div')
    container.classList.add("mermaid");

    // const pre = document.createElement('pre')
    // pre.classList.add("mermaid");
    // container.appendChild( pre );

    shadowRoot.appendChild( container );

    this.#renderDiagram()

  }


  
  /**
   * @returns {ChildNode[]}
   */
  get #textNodes() {
    return Array.from(this.childNodes).filter(
      node => node.nodeType === this.TEXT_NODE
    );
  }

  /**
   * @returns {string}
   */
  get #textContent() {
    
    if( this._content ) {

      if( this._activeClass ) {
        return `
        ${this._content}
        classDef ${this._activeClass} fill:#f96
        `
      }
      
      return this._content
    }

    return 'flowchart TD' //this.#textNodes.map(node => node.textContent?.trim()).join('');
  }


  async #renderDiagram( ) {
    const svgContainer = this.shadowRoot?.querySelector('.mermaid')

    if( !svgContainer ) {
      console.error( 'svgcontainer not found!')
      return
    } 

    return mermaid.render( 'graph', this.#textContent )
        .then( res => { 
          _LOG( 'RENDER COMPLETE', svgContainer );
          const { width, height } = svgContainer.getBoundingClientRect();
          _LOG( 'width:', width, 'height:', height);
          const translated = res.svg
            .replace( /height="[\d\.]+"/, `height="${height}"`) 
            .replace( /width="[\d\.]+"/, `width="${width}"`);
          svgContainer.innerHTML = translated;
        })
        .then( () => this.#svgPanZoom() )
        .then( () => {
          _LOG( () => {
            _LOG("boundingClientRect", svgContainer.getBoundingClientRect() );
            for( const rc of svgContainer.getClientRects() ) {
              _LOG( rc );
            }}
          )})
        .catch( e => console.error( 'RENDER ERROR', e ) )

  }

  #svgPanZoom() {

    _LOG( '_lastTransform', this._lastTransform )

    // @ts-ignore
    const svgs = d3.select( this.shadowRoot ).select(".mermaid svg");

    const self = this;

    svgs.each( function() {
      // 'this' refers to the current DOM element
      const svg = d3.select(this);
      
      svg.html("<g>" + svg.html() + '</g>');

      const inner = svg.select("g");
   
      const zoom = d3.zoom().on("zoom", event => {
          inner.attr("transform", event.transform);
          self._lastTransform = event.transform;
        }); 
      
      // @ts-ignore
      const selection = svg.call(zoom);

      if( self._lastTransform !== null ) {
        inner.attr("transform", self._lastTransform)
        // [D3.js Set initial zoom level](https://stackoverflow.com/a/46437252/521197)
        // @ts-ignore
        selection.call(zoom.transform, self._lastTransform);
      }  

    });

  }

  /**
   * Handles the content event to update the diagram content.
   *
   * @param {CustomEvent} e - The event object containing the new content detail.
   */
  #onContent(e) {
    const { detail: newContent } = e;

    this._content = newContent;
    this.#renderDiagram();
  }

  /**
   * Handles the active class event to update the active class in the diagram.
   *
   * @param {CustomEvent<NextNodeData} e - The event object containing the active class detail.
   */
  #onActive(e) {

    const { detail: { node, subgraphNode } } = e;

    this._activeClass = subgraphNode ?? node ;

    _DBG("Active class updated:", this._activeClass);

    this.#renderDiagram();  
  }

  /**
   * Handles the resize event to re-render the diagram.
   */
  #resizeHandler = () => this.#renderDiagram();

  /**
   * Called when the element is connected to the document's DOM.
   * Sets up event listeners for graph content and active class updates, and window resize.
   */
  connectedCallback() {
    // @ts-ignore
    this.addEventListener('graph', this.#onContent);
    // @ts-ignore
    this.addEventListener('graph-active', this.#onActive);
    window.addEventListener('resize', this.#resizeHandler);
  }

  /**
   * Called when the element is disconnected from the document's DOM.
   * Cleans up event listeners for graph content and active class updates, and window resize.
   */
  disconnectedCallback() {
    // @ts-ignore
    this.removeEventListener('graph', this.#onContent);
    // @ts-ignore
    this.removeEventListener('graph-active', this.#onActive);
    window.removeEventListener('resize', this.#resizeHandler);
  }

  /**
   * Renders the diagram with the current content and runs the mermaid library.
   * This method is deprecated and should not be used in new code.
   *
   * @deprecated
   * @returns {Promise<void>} A promise that resolves when the diagram rendering and mermaid run are complete.
   */
  // @ts-ignore
  async #renderDiagramWithRun() {
    const pres = this.shadowRoot?.querySelectorAll('.mermaid');
    
    // @ts-ignore
    pres[0].textContent = this.#textContent;

    return mermaid.run({
      // @ts-ignore
      nodes: pres,
      suppressErrors: true
    })
    .then(() => _LOG("RUN COMPLETE"))
    .then(() => this.#svgPanZoom())
    .catch(e => console.error("RUN ERROR", e));
  }


}

window.customElements.define('lg4j-graph', LG4jMermaid);
