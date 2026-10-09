# F001 - relocate artifactId of module langchain4j-agent

## Description

In the next release I want to relocate the artifactId of the module in folder `langchain4j/langchain4j-agent` from artifactId
`langgraph4j-agent-executor` to `langgraph4j-langchain4j-agentexecutor` in order to have a more consistent naming with the other modules of the project.

I want that you arrange all needed stuff so that the module can be published with the new artifactId and that all other modules that depend on it are updated accordingly
while the old artifactid must be deployed with indication of deprecation & relocation to the new artifactId as standard guideline of maven.