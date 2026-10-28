# F008 Example of AG-UI Sdk usage in LangGraph4j

## Instructions

I want write a LangGraph4j guide for explain how to use AG-UI Sdk in file @ag-ui/langgraph4j-ag-ui-sdk/README.md.

Currently in readme are briefly described the class published in the package @ag-ui/langgraph4j-ag-ui-sdk/src/main/java/org/bsc/langgraph4j/agui/sdk, but I want to write a more detailed guide with examples of usage.
First I want that for each class will be added a table with:
* name of public/protected method
* arguments of the method
* description of the method with the role that play in the AG-UI Sdk

After that I want to add a section with examples of usage for each class, with code snippets and explanations of how to use the methods in practice.

take a look at the  examples in @studio/base/src/test/java/org/bsc/langgraph4j/agui/AGUISampleAgent.java.
this examples contains:
* `base-agent` that is a basic example that is a reference implementation
* `agentWithInterruption` that is a more complex example that shows how to use the AG-UI Sdk with graph interruption handling.

