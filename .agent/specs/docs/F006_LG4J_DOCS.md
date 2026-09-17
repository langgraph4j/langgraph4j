# F006 - Document the new feature: Emit a custom NodeOutput from a NodeAction during graph execution

## Instructions

I want document in the project the new feature  "Emit a custom NodeOutput from a NodeAction during graph execution" from [issue #427](https://github.com/langgraph4j/langgraph4j/issues/427) related to issue [How to write custom content to the output stream in Node, just like in langgraph？](https://github.com/langgraph4j/langgraph4j/issues/402)

To get information for generate documentation you have to take a look to changes about commit hash `0700f18c1643806ede1869b1ed4d1ecd01c4c4f7` with description "Merge branch 'feature/#427_emit_custom_output' into develop"

The documentation must be generated in two different files:
* new file `src/site/mkdocs/core/emit-custom-output.md`
  in this file you must put a comprehensive description of the feature with some code snippets. Add also at top a description of the streaming nature of the graph output through method `graph.stream`
* add section in file `src/site/mkdocs/whats-new-v1.9.md`
  in this section you must put a briefly description of the feature like an aanouncement 

