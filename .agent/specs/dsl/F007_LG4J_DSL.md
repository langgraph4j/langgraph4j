# F007 refine frontend

## increase modularity and separation of concerns

After apply feature [.agent/specs/dsl/F006_LG4J_DSL.md] I want further improve frontend modularization and separation of concerns.

I want that web component `LG4JDSLViewElement` in file [langgraph4j-core/src/test/resources/static/dsl-view.js] must contains only the code strictly concerning DSL visualization and css styles related to it. 
The web component must provide a public property `src`  that will be the DSL source, when set the DSL will be rendered.

The code concerning loading of DSL and relate css styles must be separated by web component and put in `index.html`


