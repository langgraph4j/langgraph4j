# F007 extends react flow controls

## Description
In [react flow](https://reactflow.dev/) library we can extends `Controls` component to add custom controls to the graph.
This allows us to add additional functionality to the graph adding a new button take a look [here](https://reactflow.dev/api-reference/components/control-button).
I want add a new toggle button to the graph that will save the current graph layout to the `sessionStorage` and load it back when needed.
When such button is clicked (i.e.toggled), the current graph layout will be saved to the `sessionStorage` and when the graph is loaded again, it will check if there is a saved layout in the `sessionStorage` and load it back if available.
This will allow users to save their work and continue later without losing their progress after a page reload.
If the button is toggled again, remove the saved layout from the `sessionStorage` and when the page is reloaded it will use the given representation.

## Instructions

* create a new component `LayoutToggleButton` that extends the `Controls` component from react flow.
* the `LayoutToggleButton` must have a icon that indicates the current state of the layout (saved or not saved).
* During process ensure that code is refactored in a way that is compatible with typescript and includes type definitions for all functions, parameters, and return values.
  Moreover it must be written clear and concise, providing useful information for developers who will be using or maintaining the code.




