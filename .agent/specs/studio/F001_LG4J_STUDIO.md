# F001 remove Tailwindcss and DaisyUI

## Introduction 

The SPA Webapp in folder [studio/webui] has been developed using the followig tech stack.

* [Lit](https://lit.dev/docs/) a library for building fast, lightweight web components. 
* tailwindcss + [DaisyUI](https://daisyui.com/components/) for styles
* React 18
* [Parcel](https://github.com/parcel-bundler/parcel) as build tools

The main components are:

* [studio/webui/src/lg4j-executor.js]
   > This component provides a form to give input for start/resume/stop process 
* [studio/webui/src/lg4j-result.js]
   > This component provides an Accordion that show the results (steps) of the process execution
* [studio/webui/src/lg4j-workbench.js]
   > This component arrange the children components layout and route the bubbled events

## Requirements 

I want that will be removed Tailwindcss and DaisyUI styles and replaced with equivalent plain CSS ones

**Development Note**
> Currently the Tailwindcss and DaisyUI generated styles are stored in [studio/webui/src/twlit.js] that is included in every components


## Implementation summary

Implemented the Tailwindcss and DaisyUI removal for `studio/webui` by deleting the generated `twlit.js` stylesheet, the Tailwind generator/configuration files, and the tracked PostCSS Tailwind configuration. Replaced Tailwind/DaisyUI utility classes in the Lit web components with scoped plain CSS for the workbench layout, executor form/buttons/modal, result tabs and expandable result panels, and graph container. Updated the global `app.css`, removed Daisy-specific `data-theme` usage from the HTML entry points, removed Tailwind/DaisyUI-related package scripts and dependencies, simplified the deploy script so it no longer regenerates Tailwind styles, and verified the frontend with `bun run parcel:build`.
