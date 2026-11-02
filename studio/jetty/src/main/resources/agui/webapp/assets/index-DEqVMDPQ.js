import{r as e}from"./rolldown-runtime-hePW80VL.js";import{$ as t,Q as n,Z as r}from"./vendor-BtSqdUCw.js";import{n as i,t as a}from"./vendor-DpJc7UpO.js";import{i as o,n as s}from"./react-vendor-BvB9BS1T.js";import{t as c}from"./dagre-vendor-ClWv1zX3.js";import{a as l,c as u,d,f,h as p,i as m,l as h,m as g,n as _,o as v,p as y,r as b,s as x,t as ee,u as te}from"./xyflow-vendor-DMzEnb2B.js";import{t as S}from"./json-viewer-vendor-BNHwpP2j.js";(function(){let e=document.createElement(`link`).relList;if(e&&e.supports&&e.supports(`modulepreload`))return;for(let e of document.querySelectorAll(`link[rel="modulepreload"]`))n(e);new MutationObserver(e=>{for(let t of e)if(t.type===`childList`)for(let e of t.addedNodes)e.tagName===`LINK`&&e.rel===`modulepreload`&&n(e)}).observe(document,{childList:!0,subtree:!0});function t(e){let t={};return e.integrity&&(t.integrity=e.integrity),e.referrerPolicy&&(t.referrerPolicy=e.referrerPolicy),t.credentials=e.crossOrigin===`use-credentials`?`include`:e.crossOrigin===`anonymous`?`omit`:`same-origin`,t}function n(e){if(e.ep)return;e.ep=!0;let n=t(e);fetch(e.href,n)}})();var C=e=>(...t)=>{e.on&&t.length!==0&&(typeof t[0]==`function`?t[0]():console.debug(`${e.topic}: `,...t))},w=C({on:!0,topic:`LG4JWorkbench`}),T=class extends r{static{this.styles=[t`
    :host {
      display: block;
      min-height: 100vh;
      color: #e5e7eb;
      background: #0f172a;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont,
        "Segoe UI", sans-serif;
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
  `]}static{this.properties={title:{},resultPanelVisible:{type:Boolean,converter:{fromAttribute:e=>e!==`false`}}}}constructor(){super(),this.resultPanelVisible=!1}#e(){this.resultPanelVisible=!this.resultPanelVisible}#t(e,t){let{type:n,detail:r}=e;t||=n.split(`-`)[0],w(`routeEvent`,n,t);let i=new CustomEvent(n,{detail:r}),a=this.querySelector(`[slot="${t}"]`);a?a.dispatchEvent(i):console.error(`slot '${t}' not found!`)}#n(e){let{graph:t,title:n,threads:r}=e.detail;this.#t(new CustomEvent(`graph`,{detail:t})),this.#t(new CustomEvent(`init-threads`,{detail:r}),`result`),n&&(this.title=n,this.requestUpdate())}#r(e){w(`got updated event`,e),this.#t(new CustomEvent(`${e.type}`,{detail:e.detail}),`executor`)}routeAGUIEvent(e){let t=this.shadowRoot?.getElementById(`spinner`),{type:n}=e.detail;switch(n){case i.RUN_STARTED:t?.classList.remove(`hidden`),this.#i(`START`);break;case i.RUN_ERROR:t?.classList.add(`hidden`);break;case i.RUN_FINISHED:{t?.classList.add(`hidden`);let n=e.detail;n.outcome?.type===`interrupt`?this.#i(`INTERRUPTED`):n.outcome?.type===`cancelled`?this.#i(`CANCELLED`):this.#i(`END`)}break;case i.CUSTOM:e.detail.name===`cancelled`&&(t?.classList.add(`hidden`),this.#i(`CANCELLED`));break;case i.STEP_STARTED:{let t=e.detail;this.#i(t.stepName)}}this.#t(e,`graph`),this.#t(e,`result`)}#i(e){let t=this.shadowRoot?.getElementById(`message`);t&&(t.textContent=e)}connectedCallback(){super.connectedCallback(),this.addEventListener(`init`,this.#n),this.addEventListener(`result`,this.#t),this.addEventListener(`thread-updated`,this.#r),this.addEventListener(`node-updated`,this.#r),this.addEventListener(`agui-event`,this.routeAGUIEvent)}disconnectedCallback(){super.disconnectedCallback(),this.removeEventListener(`node-updated`,this.#r),this.removeEventListener(`thread-updated`,this.#r),this.removeEventListener(`result`,this.#t),this.removeEventListener(`init`,this.#n),this.removeEventListener(`agui-event`,this.routeAGUIEvent)}render(){let e=this.resultPanelVisible?`Hide result panel`:`Show result panel`;return n`
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
            title="${e}"
            aria-label="${e}"
            aria-expanded="${this.resultPanelVisible}"
            @click="${this.#e}">
            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path d="M4 7h16M4 12h16M4 17h16" fill="none" stroke="currentColor"
                stroke-width="2" stroke-linecap="round"></path>
            </svg>
          </button>
        </div>

        <div class="layout ${this.resultPanelVisible?``:`results-hidden`}">
          <div class="left-column">
            <div class="graph-panel"><slot name="graph">LEFT</slot></div>
            <div class="executor-panel"><slot name="executor">BOTTOM</slot></div>
          </div>
          <div class="result-panel"
            ?hidden="${!this.resultPanelVisible}"><slot name="result">RIGHT</slot></div>
        </div>
      </div>
    `}};window.customElements.define(`lg4j-workbench`,T);var E=e(o(),1),D=e(s(),1),O=C({on:!0,topic:`LG4JGraph`}),k=E.createElement,A=`__ROOT__`,j=50,M=32,N=40,P=64,F=40,I=`lg4j-studio.graph-layout.`,L=.74;function ne(e){let t=0;for(let n=0;n<e.length;n+=1)t=(t<<5)-t+e.charCodeAt(n)|0;return`${I}${Math.abs(t).toString(36)}`}function R(e){return!!e&&typeof e==`object`&&Number.isFinite(e.x)&&Number.isFinite(e.y)}function z(e){return!!e&&typeof e==`object`&&Number.isFinite(e.width)&&Number.isFinite(e.height)&&e.width>0&&e.height>0}function re(e){try{let t=window.sessionStorage.getItem(e);if(!t)return null;let n=JSON.parse(t);if(!n||typeof n!=`object`)return null;let r=n;if(!r.positions||typeof r.positions!=`object`||!r.sizes||typeof r.sizes!=`object`)return null;let i={};for(let[e,t]of Object.entries(r.positions))R(t)&&(i[e]={x:t.x,y:t.y});let a={};for(let[e,t]of Object.entries(r.sizes))z(t)&&(a[e]={width:t.width,height:t.height});return{positions:i,sizes:a,collapsedSubgraphs:Array.isArray(r.collapsedSubgraphs)?r.collapsedSubgraphs.filter(e=>typeof e==`string`):[]}}catch(e){return console.warn(`Unable to read saved graph layout from sessionStorage.`,e),null}}function ie(e){return new Map(Object.entries(e))}function ae(e){return new Map(Object.entries(e))}function B(e,t,n,r,i){let a={};for(let e of t){let t=n.get(e.id)||e.position;R(t)&&(a[e.id]={x:t.x,y:t.y})}let o={};for(let[e,t]of r.entries())z(t)&&(o[e]={width:t.width,height:t.height});try{window.sessionStorage.setItem(e,JSON.stringify({positions:a,sizes:o,collapsedSubgraphs:[...i]}))}catch(e){console.warn(`Unable to save graph layout to sessionStorage.`,e)}}function oe(e){try{window.sessionStorage.removeItem(e)}catch(e){console.warn(`Unable to remove saved graph layout from sessionStorage.`,e)}}function V(e){let t=Number.parseInt(e??``,10);return Number.isFinite(t)&&t>=0?t:j}function se({data:e}){let t=e?.kind===`start`?`start`:`end`;return k(`div`,{className:`circle-node ${t}`},k(l,{type:`target`,position:y.Top}),k(`span`,null,e?.label?.replaceAll(`_`,``)||t),k(l,{type:`source`,position:y.Bottom}))}function ce({data:e,selected:t}){return k(`div`,{className:e.collapsed?`subgraph-node collapsed`:`subgraph-node`},k(v,{isVisible:t,minWidth:220,minHeight:e.collapsed?56:180,onResizeEnd:e.onResizeEnd}),k(l,{type:`target`,position:e.collapsed?y.Top:y.Left}),k(`div`,{className:`subgraph-header`},k(`span`,null,e?.label||`subgraph`),k(`button`,{className:`subgraph-toggle`,title:e.collapsed?`Expand subgraph`:`Collapse subgraph`,onClick:t=>{t.stopPropagation(),e.onToggle?.()}},e.collapsed?`+`:`-`)),k(l,{type:`source`,position:e.collapsed?y.Bottom:y.Right}))}var le={circle:se,subgraph:ce};function ue(e,t,n,r){return{x:e+(n-e)*L,y:t+(r-t)*L}}function H({id:e,sourceX:t,sourceY:n,targetX:r,targetY:i,sourcePosition:a=y.Bottom,targetPosition:o=y.Top,label:s,labelStyle:c,labelShowBg:l,labelBgStyle:u,labelBgPadding:d,labelBgBorderRadius:f,style:p,markerEnd:m,markerStart:h,interactionWidth:v}){let[b]=g({sourceX:t,sourceY:n,sourcePosition:a,targetX:r,targetY:i,targetPosition:o}),x=ue(t,n,r,i);return k(_,{id:e,path:b,labelX:x.x,labelY:x.y,label:s,labelStyle:c,labelShowBg:!1,labelBgStyle:u,labelBgPadding:d,labelBgBorderRadius:f,style:p,markerEnd:m,markerStart:h,interactionWidth:v})}var U={default:H,conditional:H,parallel:H};function de({enabled:e,disabled:t,onToggle:n}){let r=e?`Remove saved layout`:`Save layout`;return k(b,{className:e?`layout-toggle-button saved`:`layout-toggle-button`,disabled:t,title:r,"aria-label":r,onClick:n},k(`svg`,{className:`layout-toggle-icon`,viewBox:`0 0 24 24`,fill:`currentColor`,"aria-hidden":!0},k(`path`,{d:`M9 3h6v5H9z`}),k(`path`,{d:`M4 16h6v5H4z`}),k(`path`,{d:`M14 16h6v5h-6z`}),k(`path`,{d:`M11 9h2v4H11z`}),k(`path`,{d:`M6 13h12v2H6z`}),k(`path`,{d:`M11 15h2v2H11z`})))}function W(e,t){let n=e.data?.kind===`subgraph`,r=e.data?.kind===`start`||e.data?.kind===`end`,i=e.data?.layoutSize;return{width:n&&i?i.width:n?320:r?54:140,height:n&&i?i.height:n?t.has(e.id)?56:300:r?54:48}}function fe(e,t,n,r,i,a,o,s){let c=W(e,t),l=i.get(e.id),u=e.data?.kind===`subgraph`&&l&&!t.has(e.id)?l:c,d=r.get(e.id),f=a===e.id,p=o===e.id,m=f&&(e.data?.kind===`start`||e.data?.kind===`end`),h=[e.className,f?`active-node`:null,m?`active-boundary-node`:null,p?`interrupted-node`:null].filter(Boolean).join(` `);return{...e,type:e.data?.kind===`subgraph`?`subgraph`:e.data?.kind===`start`||e.data?.kind===`end`?`circle`:e.type,className:h,draggable:!0,extent:e.parentId?`parent`:e.extent,position:d||e.position,sourcePosition:y.Bottom,targetPosition:y.Top,style:{width:u.width,height:u.height,...e.style||{}},data:{...e.data,active:f,interrupted:p,collapsed:e.data?.kind===`subgraph`&&t.has(e.id),onToggle:e.data?.kind===`subgraph`?()=>n(e.id):void 0,onResizeEnd:e.data?.kind===`subgraph`?(t,n)=>{i.set(e.id,{width:n.width,height:n.height}),s()}:void 0},zIndex:e.data?.kind===`subgraph`?-1:void 0}}function pe(e){return{...e,animated:e.type===`conditional`,markerEnd:{type:f.ArrowClosed},label:e.label||e.data?.condition,style:{strokeWidth:e.type===`parallel`?2:1.5,strokeDasharray:e.type===`conditional`?`6 4`:void 0,...e.style||{}}}}function me(e){let t=new Set((e.subgraphs||[]).map(e=>e.id));return e.edges.map(e=>{let n={...e};return t.has(e.target)&&(n.target=`${e.target}-__START__`,n.data={...e.data||{},originalTarget:e.target}),t.has(e.source)&&(n.source=`${e.source}-__END__`,n.data={...n.data||{},originalSource:e.source}),n})}function he(e){return new Map(e.map(e=>[e.id,e.parentId]))}function G(e,t,n){let r=t.get(e);for(;r;){if(n.has(r))return r;r=t.get(r)}return null}function ge(e,t,n){return G(e.id,t,n)!==null}function K(e,t,n){return e.map(e=>{let r=G(e.source,t,n),i=G(e.target,t,n);return{...e,source:r||e.source,target:i||e.target}}).filter((e,t,n)=>e.source!==e.target&&n.findIndex(t=>t.source===e.source&&t.target===e.target&&t.label===e.label)===t)}function _e(e){return e.parentId||A}function ve(e){let t=new Map;for(let n of e){let e=_e(n),r=t.get(e)||[];r.push(n),t.set(e,r)}return t}function ye(e,t){let n=e===A?t:N,r=e===A?t:P;return new c.graphlib.Graph().setDefaultEdgeLabel(()=>({})).setGraph({rankdir:`TB`,align:`UL`,nodesep:t,ranksep:t+M,marginx:n,marginy:r})}function be(e){return{...e,data:{...e.data||{}}}}function xe(e,t,n){for(let r of n)t.has(r.source)&&t.has(r.target)&&e.setEdge(r.source,r.target)}function Se(e,t,n,r){return t.map(t=>{let i=W(t,r),a=e.node(t.id),o=n.get(t.id)||{x:a.x-i.width/2,y:a.y-i.height/2};return{...t,position:o}})}function Ce(e,t,n,r,i){let a=ve(e),o=[],s=new Set,l=e=>{if(s.has(e))return null;s.add(e);let u=(a.get(e)||[]).map(be);if(u.length===0)return null;for(let e of u)if(e.data?.kind===`subgraph`&&!r.has(e.id)){let t=l(e.id);t&&(e.data.layoutSize=t)}let d=ye(e,i),f=new Set(u.map(e=>e.id));for(let e of u)d.setNode(e.id,W(e,r));xe(d,f,t),c.layout(d);let p=Se(d,u,n,r);o.push(...p);let m=d.graph();return{width:Math.max(320,m.width+(e===A?0:N)),height:Math.max(180,m.height+(e===A?0:F))}};l(A);for(let e of a.keys())l(e);let u=t=>{let n=0,r=t.parentId;for(;r;)n+=1,r=e.find(e=>e.id===r)?.parentId;return n};return o.sort((e,t)=>u(e)-u(t))}function we({source:e,activeNodeId:t,interruptedNodeId:n,nodeGap:r}){let[i,a]=(0,E.useState)(null),[o,s]=(0,E.useState)(new Set),[c,l,f]=te([]),[p,g,_]=h([]),[v,y]=(0,E.useState)(!0),[b,x]=(0,E.useState)(!1),[S,C]=(0,E.useState)(null),w=E.useRef(null),T=E.useRef(new Map),D=E.useRef(new Map),A=E.useRef([]),j=E.useRef(new Set),M=E.useRef(!1),N=E.useRef(null),{fitView:P}=d();E.useEffect(()=>{A.current=c},[c]),E.useEffect(()=>{j.current=o},[o]),E.useEffect(()=>{M.current=b},[b]),E.useEffect(()=>{N.current=S},[S]);let F=(0,E.useCallback)(()=>{let e=N.current;M.current&&e&&A.current.length!==0&&B(e,A.current,T.current,D.current,j.current)},[]),I=(0,E.useCallback)(e=>{s(t=>{let n=new Set(t);return n.has(e)?n.delete(e):n.add(e),j.current=n,n}),requestAnimationFrame(F)},[F]),L=(0,E.useCallback)((e,i)=>{let a=he(e.nodes),o=me(e),s=K(e.edges,a,i),c=Ce(e.nodes.filter(e=>!ge(e,a,i)),s,T.current,i,r).map(e=>fe(e,i,I,T.current,D.current,t,n,F));A.current=c,l(c),g(K(o,a,i).map(pe))},[t,n,r,F,g,l,I]),R=(0,E.useCallback)(e=>{if(v){for(let t of e)t.type===`position`&&t.position&&T.current.set(t.id,t.position);f(e),requestAnimationFrame(F)}},[f,v,F]),z=(0,E.useCallback)(e=>{let t=JSON.parse(e);if(t.type!==`langgraph4j`||!Array.isArray(t.nodes)||!Array.isArray(t.edges))throw Error(`JSON is not a Langgraph4j DSL document.`);let n=ne(e),r=re(n);T.current=r?ie(r.positions):new Map,D.current=r?ae(r.sizes):new Map;let i=new Set(r?.collapsedSubgraphs||[]);j.current=i,N.current=n,M.current=!!r,a(t),s(i),C(n),x(!!r),L(t,i)},[L]);E.useEffect(()=>{i&&L(i,o)},[L,o,i]),E.useEffect(()=>{if(e)try{z(e)}catch(e){console.error(e)}},[z,e]),E.useEffect(()=>{c.length!==0&&requestAnimationFrame(()=>P({padding:.16,duration:200}))},[P,c.length]),E.useEffect(()=>{requestAnimationFrame(F)},[o,c,F]),E.useEffect(()=>{if(!w.current)return;let e=new ResizeObserver(e=>{let t=e[0];t&&t.contentRect.width!==0&&t.contentRect.height!==0&&c.length!==0&&requestAnimationFrame(()=>P({padding:.16,duration:120}))});return e.observe(w.current),()=>e.disconnect()},[P,c.length]);let V=(0,E.useCallback)(()=>{let e=N.current;e&&(M.current?(oe(e),M.current=!1,x(!1)):(B(e,A.current,T.current,D.current,j.current),M.current=!0,x(!0)))},[]);return(0,E.useMemo)(()=>k(`div`,{className:`flow-wrapper`,ref:w},k(u,{nodes:c,edges:p,nodeTypes:le,edgeTypes:U,onNodesChange:R,onEdgesChange:_,fitView:!0,fitViewOptions:{padding:.16},minZoom:.2,maxZoom:1.5,style:{width:`100%`,height:`100%`},nodesDraggable:v,elementsSelectable:v,nodesConnectable:v},k(m,{onInteractiveChange:e=>{O(`Interactive status changed:`,e),y(!v)}},k(de,{enabled:b,disabled:!S||c.length===0,onToggle:V})),k(ee,{gap:18,size:1}))),[p,V,R,v,b,S,c,_])}function Te(){return`
    ${p}

    :host {
      display: block;
      width: 100%;
      height: 100%;
      min-height: 100%;
      color-scheme: light;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: #f6f7f9;
      color: #1f2933;
    }

    * {
      box-sizing: border-box;
    }

    .mount {
      width: 100%;
      height: 100%;
      min-width: 0;
      min-height: 0;
    }

    .layout-toggle-button {
      color: #4b5563;
    }

    .layout-toggle-button.saved {
      color: #047857;
      background: #ecfdf5;
    }

    .layout-toggle-button:disabled {
      color: #9ca3af;
      cursor: not-allowed;
    }

    .layout-toggle-icon {
      width: 16px;
      height: 16px;
      display: block;
    }

    .app {
      width: 100%;
      height: 100%;
      min-height: 0;
      display: block;
    }

    .graph {
      width: 100%;
      height: 100%;
      min-width: 0;
      min-height: 0;
      background: #eef2f7;
    }

    .flow-wrapper {
      width: 100%;
      height: 100%;
      min-width: 0;
      min-height: 0;
    }

    .react-flow__node-default {
      border-radius: 6px;
      border: 1px solid #b8c2d1;
      background: #ffffff;
      color: #1f2933;
      font-size: var(--lg4j-workbench-font-size, 12px);
      min-width: 120px;
      text-align: center;
    }

    .circle-node {
      width: 54px;
      height: 54px;
      display: grid;
      place-items: center;
      border-radius: 999px;
      border: 2px solid #64748b;
      background: #ffffff;
      color: #17212f;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-weight: 700;
      text-transform: uppercase;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.12);
    }

    .circle-node.start {
      border-color: #16a34a;
      background: #ecfdf3;
    }

    .circle-node.end {
      border-color: #dc2626;
      background: #fef2f2;
    }

    .subgraph-node {
      width: 100%;
      height: 100%;
      min-width: 220px;
      min-height: 72px;
      border: 1px dashed #64748b;
      border-radius: 8px;
      background: rgba(255, 255, 255, 0.64);
      color: #1f2933;
      overflow: hidden;
    }

    .subgraph-header {
      height: 36px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 0 10px;
      border-bottom: 1px solid rgba(100, 116, 139, 0.28);
      background: rgba(248, 250, 252, 0.9);
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-weight: 700;
    }

    .subgraph-toggle {
      width: 26px;
      height: 26px;
      display: grid;
      place-items: center;
      border: 1px solid #c9d1dc;
      border-radius: 6px;
      background: #ffffff;
      color: #1f2933;
      cursor: pointer;
    }

    .subgraph-node.collapsed {
      min-height: 56px;
    }

    .react-flow__node.active-node {
      filter: drop-shadow(0 0 10px rgba(37, 99, 235, 0.35));
    }

    .react-flow__node.active-node::after {
      content: "";
      position: absolute;
      top: -8px;
      right: -8px;
      width: 18px;
      height: 18px;
      border: 3px solid #bfdbfe;
      border-top-color: #2563eb;
      border-radius: 999px;
      background: #ffffff;
      animation: lg4j-spin 0.8s linear infinite;
      z-index: 2;
    }

    .react-flow__node.active-boundary-node::after {
      content: none;
    }

    .react-flow__node.active-node.react-flow__node-default {
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.18);
    }

    .react-flow__node.active-node .circle-node,
    .react-flow__node.active-node .subgraph-node {
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.18);
    }

    .react-flow__node.interrupted-node {
      filter: drop-shadow(0 0 10px rgba(220, 38, 38, 0.35));
    }

    .react-flow__node.interrupted-node::after {
      content: none;
    }

    .react-flow__node.interrupted-node.react-flow__node-default {
      border-color: #dc2626;
      box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.22);
    }

    .react-flow__node.interrupted-node .circle-node,
    .react-flow__node.interrupted-node .subgraph-node {
      border-color: #dc2626;
      box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.22);
    }

    @keyframes lg4j-spin {
      to {
        transform: rotate(360deg);
      }
    }

  `}var Ee=class extends HTMLElement{static get observedAttributes(){return[`node-gap`]}constructor(){super();let e=this.attachShadow({mode:`open`}),t=document.createElement(`style`);t.textContent=Te(),this.mount=document.createElement(`div`),this.mount.className=`mount`,e.append(t,this.mount),this.root=null,this.source=void 0,this.activeNodeId=void 0,this.lastActiveNodeId=void 0,this.interruptedNodeId=void 0}#e(){this.activeNodeId=void 0,this.interruptedNodeId=this.lastActiveNodeId,this.update()}attributeChangedCallback(){this.update()}connectedCallback(){this.root||=(0,D.createRoot)(this.mount),this.addEventListener(`graph`,this.render),this.addEventListener(`agui-event`,this.onAGUIEvent)}disconnectedCallback(){this.removeEventListener(`graph`,this.render),this.removeEventListener(`agui-event`,this.onAGUIEvent),this.root?.unmount(),this.root=null}render(e){this.source=e.detail,this.update()}onAGUIEvent(e){O(`process event `,e);let t=e.detail;switch(this.interruptedNodeId=void 0,t.type){case i.RUN_STARTED:this.activeNodeId=`__START__`;break;case i.RUN_FINISHED:{let e=t;if(e.outcome&&e.outcome.type===`interrupt`){let t=e.outcome;O(`graph interrupted`,t.interrupts),this.#e();return}if(e.outcome&&e.outcome.type===`cancelled`){O(`graph cancelled`),this.interruptedNodeId=this.activeNodeId;break}this.activeNodeId=`__END__`}break;case i.STEP_STARTED:{let e=t;this.activeNodeId=e.stepName}break;case i.STEP_FINISHED:{let e=t;this.lastActiveNodeId=e.stepName}break;case i.CUSTOM:t.name===`cancelled`&&(O(`graph cancelled via custom event`),this.interruptedNodeId=this.activeNodeId);break;case i.RUN_ERROR:}this.update()}update(){this.root?.render(k(`main`,{className:`app`},k(`section`,{className:`graph`},k(x,null,k(we,{source:this.source,activeNodeId:this.activeNodeId,interruptedNodeId:this.interruptedNodeId,nodeGap:V(this.getAttribute(`node-gap`))})))))}};customElements.define(`lg4j-graph`,Ee);var q=class{constructor(e=[]){this.items=e}push(e){return this.items.unshift(e)}pop(){return this.items.shift()}peek(){return this.items[0]}get elements(){return this.items}isEmpty(){return this.items.length===0}get size(){return this.items.length}clear(){this.items=[]}},J=C({on:!0,topic:`LG4JResult`}),Y=C({on:!0,topic:`LG4JResult`}),X=class extends r{static{this.styles=[t`
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
  `]}static{this.properties={}}#e;get selectedTab(){return this.#e}set selectedTab(e){this.#e=e,this.dispatchEvent(new CustomEvent(`thread-updated`,{detail:e,bubbles:!0,composed:!0,cancelable:!0}))}constructor(){super(),this.threadMap=new Map,this.#t=e=>{let{detail:t=[]}=e;J(`threads`,t);let n=t.map(([e,t])=>[e,new q(t)]);this.threadMap=new Map(n),t&&t.length>0&&(this.selectedTab=t[0][0],this.requestUpdate())},this.#n=e=>{let[t,n]=e.detail;if(J(`ON RESULT`,t,n),!this.threadMap.has(t))throw Error(`result doesn't contain a valid thread! ${t}`);let r=this.threadMap.get(t);if(!r)throw Error(`thread "${t} doesn't contain a valid stack! `);let i=r.peek(),a=i?i.push(n):r.push([n]);n.cancelled&&r.push([]),this.threadMap.set(t,r),this.requestUpdate(),this.updateComplete.then(()=>{let e=`#json${a-1}`,t=this.shadowRoot.querySelectorAll(e);J(e,t);for(let e of t)e.expandAll()})}}connectedCallback(){super.connectedCallback(),this.addEventListener(`result`,this.#n),this.addEventListener(`init-threads`,this.#t),this.addEventListener(`node-updated`,this.#a),this.addEventListener(`agui-event`,this.onAGUIEvent)}disconnectedCallback(){super.disconnectedCallback(),this.removeEventListener(`result`,this.#n),this.removeEventListener(`init-threads`,this.#t),this.removeEventListener(`node-updated`,this.#a),this.removeEventListener(`agui-event`,this.onAGUIEvent)}onAGUIEvent(e){Y(`process event `,e);let t=e.detail;switch(t.type){case i.CUSTOM:{let e=t;if(Y(`customEvent`,e),e.name===`cancelled`){Y(`graph cancelled via custom event`);return}this.#n(new CustomEvent(`custom`,{detail:e.value}))}break;case i.RUN_FINISHED:{let e=t;e.outcome&&(e.outcome.type===`interrupt`?Y(`graph interrupted`):e.outcome.type===`cancelled`?Y(`graph cancelled`):this.selectedTab&&this.threadMap.get(this.selectedTab)?.push([]))}case i.STEP_FINISHED:case i.RUN_STARTED:case i.STEP_STARTED:case i.RUN_ERROR:}}#t;#n;#r(e){let{id:t}=e.target;J(`onSelectTab`,t),this.selectedTab=t,this.requestUpdate()}#i(e){J(`NEW TAB`,e);let t=`Thread-${this.threadMap.size+1}`;this.threadMap.set(t,new q),this.selectedTab=t,this.requestUpdate()}#a(e){J(`onNodeUpdated`,e)}#o(e,t){return n`
    <details>
      <summary>${e.cancelled?`"CANCELLED"`:e.node}</summary>
      <div class="details-content">
        <lg4j-node-output id="(${e.node})[${t}]" value="${JSON.stringify(e).trim()}"></lg4j-node-output>
      </div>
    </details>
    `}#s(){return this.selectedTab?this.threadMap.get(this.selectedTab)?.elements.filter(e=>e.length>0).map((e,t)=>n`
          <details class="execution" ?open="${t===0}">
            <summary>${t===0?`Last Execution`:`Execution (${t})`}</summary>
            <div class="details-content">
              <table>
                <tbody>
                  ${e.map(e=>n`<tr><td>${this.#o(e,t)}</td></tr>`)}
                </tbody>
              </table>
            </div>
          </details>`):n`<div class="alert">No Data</div>`}#c(){let e=[...this.threadMap.keys()];return n`
    ${e.map(e=>n`<a id="${e}" @click="${this.#r}" role="tab" class="tab ${this.selectedTab===e?`tab-active`:``}" >${e}</a>`)}
    `}render(){return n`
      
      <div class="result-root">
        <div role="tablist" class="tabs">
            ${this.#c()}
            <a role="tab" class="tab add-tab" @click="${this.#i}">
              <svg  xmlns="http://www.w3.org/2000/svg" width="1rem" height="1rem" viewBox="0 0 20 20">
                <circle cx="10" cy="10" r="9" fill="none" stroke="white" stroke-width="1.5"/>
                <line x1="5" y1="10" x2="15" y2="10" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
                <line x1="10" y1="5" x2="10" y2="15" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </a>
          </div>
            <div class="results-panel">
            ${this.#s()}
            </div>
        </div> 
    `}};window.customElements.define(`lg4j-result`,X);var De=class extends r{static{this.styles=t`
    :host {
      display: block;
      font-family: Arial, sans-serif;
    }
    .uploader {
      display: flex;
      flex-direction: row;
      justify-content: center;
      align-items: center;
      gap: 10px;
      border: 1px dashed #ccc;
      border-radius: 4px;
      padding: 1px;
      text-align: center;
      cursor: pointer;
    }
    .uploader:hover {
      border-color: #aaa;
    }
    input[type="file"] {
      display: none;
    }
    .thumbnail {
      width: 50px; /* Fixed width */
      height: 50px; /* Fixed height */
      object-fit: cover; /* Maintain aspect ratio, crop if necessary */
      border-radius: 8px; /* Optional: rounded corners */
      margin-top: 10px;
    }
  `}static{this.properties={_file:{state:!0},nopreview:{attribute:`no-preview`,type:Boolean}}}constructor(){super(),this.nopreview=!1,this._file=null,this._base64=null}get value(){return this._base64}get#e(){return this._file&&URL.createObjectURL(this._file)}render(){return n`
      <div class="uploader" @click=${this._openFileDialog}>
        <input type="file" @change=${this._handleFileUpload} accept="image/*">
        ${this.#e?this.nopreview?n`<p>Uploaded File: ${this._file?.name}</p>`:n`<p>Uploaded Image:</p><img class="thumbnail" src=${this.#e} alt="Uploaded image">`:n`<p>Click here or drag and drop an image file</p>`}
      </div>
    `}_openFileDialog(){(this.shadowRoot?.querySelector(`input[type="file"]`))?.click()}_handleFileUpload(e){let t=e.target?.files[0];t&&t.type.startsWith(`image/`)?(this._file=t,this.convertFileToBase64(t).then(e=>this._base64=e).catch(e=>alert(e.message))):alert(`Please select a valid image file.`)}convertFileToBase64(e){return new Promise((t,n)=>{let r=new FileReader;r.onload=()=>{let e=r.result;if(typeof e==`string`)t(e.split(`,`)[1]);else if(e instanceof ArrayBuffer){let n=new Uint8Array(e).reduce((e,t)=>e+String.fromCharCode(t),``);t(btoa(n))}else n(Error(e?`Unknown type of file`:`Failed to read file`))},r.onerror=n,r.readAsDataURL(e)})}};customElements.define(`lg4j-image-uploader`,De);var Z=C({on:!0,topic:`LG4JExecutor`}),Oe=C({on:!0,topic:`LG4JViewerExecutor`}),ke=class extends Error{constructor(e){super(e.statusText||`Retrieve data error`)}},Q=class extends r{static{this.styles=[t`
    :host {
      display: block;
      color: #e5e7eb;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont,
        "Segoe UI", sans-serif;
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
      transition: background 0.15s ease, border-color 0.15s ease, opacity 0.15s
        ease;
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
  `]}static{this.properties={url:{type:String,reflect:!0},_executing:{state:!0}}}#e;#t;constructor(){super(),this._selectedThread=void 0,this.#e=null,this.#t=void 0,this.formMetaData=[],this.subscription=null,this.url=null,this._executing=!1}get _contextPath(){let e=new URL(this.url||`${window.location.protocol}//${window.location.host}`);return(this.url?e.toString():e.pathname).replace(/\/+$/,``).replace(/\/+$/,``)}#n(){this._executing=!0}#r(e){this._executing=!1}#i(e){Z(`thread-updated`,e.detail),this._selectedThread=e.detail,this.#e=null,this.requestUpdate()}#a(e){Z(`onNodeUpdated`,e),this.#e=e.detail,this.requestUpdate()}connectedCallback(){super.connectedCallback(),this.addEventListener(`thread-updated`,this.#i),this.addEventListener(`node-updated`,this.#a),this._callInit()}disconnectedCallback(){super.disconnectedCallback(),this.removeEventListener(`thread-updated`,this.#i),this.removeEventListener(`node-updated`,this.#a)}#o(e){let t=this.shadowRoot?.getElementById(`error_dialog`);if(t&&`showModal`in t){let n=t.querySelector(`#error_message`);n&&(n.textContent=e),t.showModal()}}async _callInit(){let e=`${this._contextPath}/init${window.location.search}`;Z(`initUrl`,e);let t=await fetch(e,{method:`GET`,credentials:`include`});if(!t.ok)return this.#o(t.statusText),null;let n=await t.json();Z(`initData`,n),this.dispatchEvent(new CustomEvent(`init`,{detail:n,bubbles:!0,composed:!0,cancelable:!0})),this.#t=n.id,this.formMetaData=n.args,this.requestUpdate()}async#s(){let e=this.#e;if(!e)return;let t={runId:`run1`,threadId:this._selectedThread,state:{},messages:[],tools:[],context:[],resume:[{interruptId:e.node,status:`resolved`,payload:{checkpoint:e.checkpoint,data:JSON.stringify(e.data)},metadata:void 0}]};await this.runAgent(t)}async#c(){if(!this._executing)return;let e=await fetch(`${this._contextPath}/stream/${this.#t}?thread=${this._selectedThread}&interrupt=true`,{method:`DELETE`,credentials:`include`});if(!e.ok)throw new ke(e)}async _callSubmit(){Z(`callSubmit`,this.formMetaData);let e=this.formMetaData.reduce((e,t)=>{let{name:n,type:r}=t,i=this.shadowRoot?.getElementById(n)?.value;if(i)switch(r){case`STRING`:e.push({id:n,role:`user`,content:i});break;case`IMAGE`:throw Error(`IMAGE type not yet implemented`)}return e},[]),t={runId:`run1`,threadId:this._selectedThread,state:{},messages:e,tools:[],context:[]};await this.runAgent(t)}async runAgent(e){this.#n();let t=e=>{let t;t=e instanceof Error?e:Error(JSON.stringify(e)),this.#o(t.message),this.#r(t)};try{let n=new a({url:`${this._contextPath}/stream/${this.#t}?thread=${this._selectedThread}`,fetch:(e,t)=>fetch(e,{...t,credentials:`include`})});this.subscription=n.run(e).subscribe({next:e=>{Z(`Received event: `,JSON.stringify(e));let t=new CustomEvent(`agui-event`,{detail:e,bubbles:!0,composed:!0,cancelable:!0});this.dispatchEvent(t)},error:e=>t(e),complete:()=>this.#r(null)})}catch(e){t(e)}}render(){return n`
      <div class="container">
        ${this.formMetaData.map(({name:e,type:t})=>{switch(t){case`STRING`:return n`<textarea id="${e}" placeholder="${e}"></textarea>`;case`IMAGE`:return n`<lg4j-image-uploader id="${e}"></lg4j-image-uploader>`}})}
        <div class="commands">
          <button id="submit" ?disabled=${this._executing} @click="${this._callSubmit}" class="primary item1">Submit</button>
          <button id="resume" ?disabled=${!this.#e||this._executing} @click="${this.#s}" class="secondary item2">
          Resume ${this.#e?`(from `+this.#e?.node+`)`:``}
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
    `}},Ae=class extends Q{static{this.styles=[...Q.styles,t`
      .container {
        display: none;
      }
    `]}connectedCallback(){this.hidden=!0,this.setAttribute(`aria-hidden`,`true`),super.connectedCallback()}get _contextPath(){return super._contextPath.concat(`/viewer`)}async _callInit(){Oe(`_callInit`);let e=await super._callInit();return setTimeout(async()=>{this._selectedThread=`default`,await this._callSubmit()},1e3),e}};window.customElements.define(`lg4j-executor`,Q),window.customElements.define(`lg4j-viewer-executor`,Ae);var $=C({on:!0,topic:`LG4JNodeOutput`}),je=class extends HTMLElement{static get observedAttributes(){return[`value`]}constructor(){super();let e=this.attachShadow({mode:`open`}),t=document.createElement(`style`);t.textContent=`
      <style>
      </style>
      `,e.appendChild(t)}attributeChangedCallback(e,t,n){e===`value`&&n!==null&&n!==t&&($(`attributeChangedCallback.value`,n),this.root=this.#t(JSON.parse(n)))}connectedCallback(){}disconnectedCallback(){$(`disconnectedCallback`),this.root?.unmount()}get isCollapsed(){return this.getAttribute(`collapsed`)===`true`}#e(e,t){if(t.checkpoint){let n={node:t.node,checkpoint:t.checkpoint,data:e.updated_src};return this.dispatchEvent(new CustomEvent(`node-updated`,{detail:n,bubbles:!0,composed:!0,cancelable:!0})),!0}return!1}#t(e){let t=`json-view-${this.id}`;this?.shadowRoot?.getElementById(t)?.remove();let n=document.createElement(`span`);n.setAttribute(`id`,t),this.shadowRoot?.appendChild(n);let r=D.createRoot(n),i=E.createElement(S,{src:e.state,enableClipboard:!1,displayDataTypes:!1,name:!1,collapsed:this.isCollapsed,theme:`monokai`,onEdit:t=>this.#e(t,e),validationMessage:`Read only`});return r.render(i),r}};window.customElements.define(`lg4j-node-output`,je);