# F002 Create Langgraph4j DSL schema

## Schema generation

Concerning the implemented feature @.agent/specs/F001_LG4J_DSL.md we need that you generate a JSON schema belong to generated DSL. 

write the schema in folder langgraph4j-core/src/test/resources


## Implementation summary

Implemented the Langgraph4j DSL JSON Schema as `langgraph4j-core/src/test/resources/langgraph4j-dsl.schema.json` using JSON Schema draft 2020-12. The schema documents and constrains the current `CompiledGraph.toJSON()` output contract, including root metadata, React Flow-compatible node fields, edge fields, nested subgraph metadata, known `type`/`kind` values, node positioning, and parent-child graph relationships. Added a regression test that loads the schema resource and verifies generated DSL values stay aligned with the schema definitions.
