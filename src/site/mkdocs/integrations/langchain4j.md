# <img src="https://docs.langchain4j.dev/img/logo.svg" alt="logo" width="25"/> LangChain4j Integrations


[LangGraph4j] seamlessly integrates with [LangChain4j] enabling developers to build powerful LLM-based applications using familiar tools in the Java ecosystem.

## Features

-	**Graph Nodes as Langchain4j Components**: You can use [LangChain4j] tools (LLM, prompt templates, retrievers, etc.) as nodes in your LangGraph4j StateGraph.
-	**Function Call Handling**: Automatically supports [LangChain4j]’s structured function calling, parsing arguments directly into graph state.
-	**Chain of Thought**: Combine multiple [LangChain4j] calls in sequence, branching based on results.
-	**Streaming**: [LangChain4j]’s streaming responses can be processed and streamed through [LangGraph4j]’s async graph.


## Benefits

✅ No need to rebuild logic – reuse your Langchain4j services

✅ Compose LLM workflows visually or programmatically

✅ Scalable, conditional execution of LLM calls

✅ Easy to debug and visualize


## Adding Dependencies

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-langchain4j</artifactId>
    <version>1.8.17</version>
</dependency>
```

## Share LangGraph4j's state to tools

When you use [LangGraph4j] service `LC4jToolService` to invoke a tool you can pass the State throught the [LangChain4j] `InvocationParameters` see snippets below:


```java
//
// Passing State information to the call
//

// create tool service
var toolService = LC4jToolService.builder()
                         .toolsFromObject( tools )
                         .build();

ToolExecutionRequest toolExecutionRequest = ... // The object returned by LLM response to notify tool invocation request

Map<String,Object> state = Map.of( "arg1", "value1" )

Command callResult = toolService.execute(
                            List.of(toolExecutionRequest),
                            InvocationContext.builder()
                                    .invocationParameters(InvocationParameters.from(state))
                                    .build()
                            , "messages" ).join();
 
```

```java
//
// Retrieve State information from the tool
//
 class Tools {

    @Tool("tool for test passing context")
    String execTestWithContext(@P("test message") String message, InvocationParameters context ) {

        assertNotNull( context );
        assertEquals( "value1", context.get("arg1") );

        return "test tool executed: %s with context".formatted( message );
    }

}
```

It is also possible update state from the tool using `SpringAIToolResponseBuilder` 

```java
class Tools {
    //
    // Update State information from the tool
    //
    @Tool("tool for test passing context and return command")
    String execTestWithContextAndReturnCommand(@P("test message") String message, InvocationParameters context ) {

        assertNotNull( context );
        assertEquals( "value1", context.get("arg1") );

        return LC4jToolResponseBuilder.of( context )
                .update( Map.of( "arg2", "value2"))
                .buildAndReturn( "test tool executed: %s with context".formatted(message) );
    }
}
```


## ReACT Agent

The **Agent Executor** is a **runtime for agents**.

#### Diagram 

![diagram](../images/agentexecutor.puml.png)

#### How to use

```java
public class TestTool {
    private String lastResult;

    Optional<String> lastResult() {
        return Optional.ofNullable(lastResult);
    }

    @Tool("tool for test AI agent executor")
    String execTest(@P("test message") String message) {

        lastResult = format( "test tool executed: %s", message);
        return lastResult;
    }
}

public void main( String args[] ) throws Exception {

    var toolSpecification = ToolSpecification.builder()
            .name("getPCName")
            .description("Returns a String - PC name the AI is currently running in. Returns null if station is not running")
            .build();

    var toolExecutor = (toolExecutionRequest, memoryId) -> getPCName();

    var chatModel = OpenAiChatModel.builder()
            .apiKey( System.getenv( "OPENAI_API_KEY" ) )
            .modelName( "gpt-4o-mini" )
            .logResponses(true)
            .maxRetries(2)
            .temperature(0.0)
            .maxTokens(2000)
            .build();


    var agentExecutor = AgentExecutor.builder()
            .chatModel(chatModel)
            // add dynamic tool
            .toolsFromObject(new TestTool())
            // add dynamic tool
            .tool(toolSpecification, toolExecutor)
            .build();

    var workflow = agentExecutor.compile();

    var iterator =  workflow.stream( Map.of( "messages", UserMessage.from("Run my test!") ) );

    for( var step : iterator ) {
        System.out.println( step );
    }
}
```


[LangChain4j]: https://docs.langchain4j.dev
[langgraph4j]: https://github.com/langgraph4j/langgraph4j