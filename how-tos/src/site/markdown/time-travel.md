# How to view and update past graph state

Once you start [checkpointing](./persistence.ipynb) your graphs, you can easily
**get** or **update** the state of the agent at any point in time. This permits
a few things:

1. You can surface a state during an interrupt to a user to let them accept an
   action.
2. You can **rewind** the graph to reproduce or avoid issues.
3. You can **modify** the state to embed your agent into a larger system, or to
   let the user better control its actions.

The key methods used for this functionality are:

- [getState](https://langgraph4j.github.io/langgraph4j/apidocs/org/bsc/langgraph4j/CompiledGraph.html#getState-org.bsc.langgraph4j.RunnableConfig-):
  fetch the values from the target config
- [updateState](https://langgraph4j.github.io/langgraph4j/apidocs/org/bsc/langgraph4j/CompiledGraph.html#updateState-org.bsc.langgraph4j.RunnableConfig-java.util.Map-java.lang.String-):
  apply the given values to the target state

**Note:** this requires passing in a checkpointer.

This works for [StateGraph](https://langgraph4j.github.io/langgraph4j/apidocs/org/bsc/langgraph4j/StateGraph.html)

Below is an example.


**Initialize logger**


```java
try( var file = new java.io.FileInputStream("./logging.properties")) {
    java.util.logging.LogManager.getLogManager().readConfiguration( file );
}

var log = org.slf4j.LoggerFactory.getLogger("time-travel");
```

## Define the state

State is an (immutable) data class, inheriting from prebuilt [MessagesState], shared with all nodes in our graph. A state is basically a wrapper of a `Map<String,Object>` that provides some enhancers:

1. Schema (optional), that is a `Map<String,Channel>` where each [`Channel`] describe behaviour of the related property
1. `value()` accessors that inspect Map an return an Optional of value contained and cast to the required type

[`Channel`]: https://langgraph4j.github.io/langgraph4j/apidocs/org/bsc/langgraph4j/state/Channel.html
[MessagesState]: https://langgraph4j.github.io/langgraph4j/apidocs/org/bsc/langgraph4j/prebuilt/MessagesState.html


```java
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.Channel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;

public class State extends MessagesState<ChatMessage> {

    public State(Map<String, Object> initData) {
        super( initData  );
    }


}
```

## Set up the tools

Using [langchain4j], We will first define the tools we want to use. For this simple example, we will
use create a placeholder search engine. However, it is really easy to create
your own tools - see documentation
[here][tools] on how to do
that.

[langchain4j]: https://docs.langchain4j.dev
[tools]: https://docs.langchain4j.dev/tutorials/tools


```java
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.Optional;

import static java.lang.String.format;

public class SearchTool {

    @Tool("Use to surf the web, fetch current information, check the weather, and retrieve other information.")
    String execQuery(@P("The query to use in your search.") String query) {

        // This is a placeholder for the actual implementation
        return "Cold, with a low of 13 degrees";
    }
}
```

## Set up the model

Now we will load the
[chat model].

1. It should work with messages. We will represent all agent state in the form of messages, so it needs to be able to work well with them.
2. It should work with [tool calling],meaning it can return function arguments in its response.

Note:
   >
   > These model requirements are not general requirements for using LangGraph4j - they are just requirements for this one example.
   >

[chat model]: https://docs.langchain4j.dev/tutorials/chat-and-language-models
[tool calling]: https://docs.langchain4j.dev/tutorials/tools   



```java
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;

var model = OpenAiChatModel.builder()
    .apiKey( System.getenv("OPENAI_API_KEY") )
    .modelName( "gpt-4o-mini" )
    .logResponses(true)
    .maxRetries(2)
    .temperature(0.0)
    .maxTokens(2000)
    .build()  

```

## Test function calling


```java
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;

var toolService = LC4jToolService.builder()
                        .toolsFromObject( new SearchTool() )
                        .build();

var tools = toolService.toolSpecifications();

UserMessage userMessage = UserMessage.from("What will the weather be like in London tomorrow?");

var params = ChatRequestParameters.builder()
                .toolSpecifications( tools )
                .build();
var request = ChatRequest.builder()
                .parameters( params )
                .messages( userMessage )
                .build();

var response = model.chat( request );

var result = toolService.execute( response.aiMessage().toolExecutionRequests() );

result;

```

    execute: execQuery 





    Optional[ToolExecutionResultMessage { id = "call_BQrdwiLO0FekSkrcyJodeIvN" toolName = "execQuery" text = "Cold, with a low of 13 degrees" }]



## Define the graph

We can now put it all together. We will run it first without a checkpointer:



```java
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import org.bsc.langgraph4j.checkpoint.MemorySaver; 
import org.bsc.langgraph4j.CompileConfig; 
import java.util.stream.Collectors;

// Route Message 
EdgeAction<State> routeMessage = state -> {
  
  var lastMessage = state.lastMessage();
  
  if ( !lastMessage.isPresent()) return "exit";

  if( lastMessage.get() instanceof AiMessage message  ) {

    // If tools should be called
    if ( message.hasToolExecutionRequests() ) return "next";
    
  }
  
  // If no tools are called, we can finish (respond to the user)
  return "exit";
};

// Call Model
NodeAction<State> callModel = state -> {
  var tools = ToolSpecifications.toolSpecificationsFrom( SearchTool.class );

  var params = ChatRequestParameters.builder()
                .toolSpecifications( tools )
                .build();
  var request = ChatRequest.builder()
                .parameters( params )
                .messages( state.messages() )
                .build();

  var response = model.chat( request );
  

  return Map.of( "messages", response.aiMessage() );
};

// Invoke Tool 
NodeAction<State> invokeTool = state -> {
  var lastMessage = (AiMessage)state.lastMessage()
                          .orElseThrow( () -> ( new IllegalStateException( "last message not found!")) );

  var toolNode = ToolNode.of( new SearchTool() );
  
  var result = toolNode.execute( lastMessage.toolExecutionRequests() )
                    .orElseThrow( () -> ( new IllegalStateException( "tool execution failed!")));

  return Map.of( "messages", result );
};

// Define Graph
var workflow = new StateGraph<State>(State.SCHEMA, stateSerializer)
  .addNode("agent", node_async(callModel) )
  .addNode("tools", node_async(invokeTool) )
  .addEdge(START, "agent")
  .addConditionalEdges("agent", edge_async(routeMessage), Map.of( "next", "tools", "exit", END ))
  .addEdge("tools", "agent");

// Here we only save in-memory
var memory = new MemorySaver();

var compileConfig = CompileConfig.builder()
                    .checkpointSaver(memory)
                    .releaseThread(false) // DON'T release thread after completion
                    .build();

var graph = workflow.compile(compileConfig);
```

## Interacting with the Agent

We can now interact with the agent. Between interactions you can get and update state.


```java
import org.bsc.langgraph4j.RunnableConfig;

var runnableConfig =  RunnableConfig.builder()
                .threadId("conversation-num-1" )
                .build();

Map<String,Object> inputs = Map.of( "messages", UserMessage.from("Hi I'm Bartolo.") );

var result = graph.stream( inputs, runnableConfig );

for( var r : result ) {
  System.out.println( r.node() );
  System.out.println( r.state() );
  
}
```

    START 


    __START__
    {messages=[UserMessage { name = null contents = [TextContent { text = "Hi I'm Bartolo." }] }]}
    agent
    {messages=[UserMessage { name = null contents = [TextContent { text = "Hi I'm Bartolo." }] }, AiMessage { text = "Hello Bartolo! How can I assist you today?" toolExecutionRequests = [] }]}
    __END__
    {messages=[UserMessage { name = null contents = [TextContent { text = "Hi I'm Bartolo." }] }, AiMessage { text = "Hello Bartolo! How can I assist you today?" toolExecutionRequests = [] }]}


Here you can see the "`agent`" node ran, and then our edge returned `__END__` so the graph stopped execution there.

Let's check the current graph state.


```java
import org.bsc.langgraph4j.checkpoint.Checkpoint;

var checkpoint = graph.getState(runnableConfig);

System.out.println(checkpoint);

```

    StateSnapshot{node=agent, state={messages=[UserMessage { name = null contents = [TextContent { text = "Hi I'm Bartolo." }] }, AiMessage { text = "Hello Bartolo! How can I assist you today?" toolExecutionRequests = [] }]}, config=RunnableConfig{ threadId=conversation-num-1, checkPointId=bb1ab271-752b-4e75-a9fa-5def60da482a, nextNode=__END__, streamMode=VALUES }}


The current state is the two messages we've seen above, 1. the Human Message we sent in, 2. the AIMessage we got back from the model.

The next value is `__END__`  since the graph has terminated.


```java
checkpoint.getNext()
```




    __END__



## Let's get it to execute a tool

When we call the graph again, it will create a checkpoint after each internal execution step. Let's get it to run a tool, then look at the checkpoint.


```java

Map<String,Object> inputs = Map.of( "messages", UserMessage.from("What's the weather like in SF currently?") );

var state = graph.invoke( inputs, runnableConfig ).orElseThrow( () ->(new IllegalStateException()) ) ;

System.out.println( state.lastMessage().orElse(null) );
  
```

    START 
    execute: execQuery 


    AiMessage { text = "The current weather in San Francisco is cold, with a low of 13 degrees Celsius. If you need more details or updates, just let me know!" toolExecutionRequests = [] }


## Pause before tools

If you notice below, we now will add interruptBefore=["action"] - this means that before any actions are taken we pause. This is a great moment to allow the user to correct and update the state! This is very useful when you want to have a human-in-the-loop to validate (and potentially change) the action to take.



```java
var memory = new MemorySaver();

var compileConfig = CompileConfig.builder()
                    .checkpointSaver(memory)
                    .releaseThread(false) // DON'T release thread after completion
                    .interruptBefore( "tools")
                    .build();

var graphWithInterrupt = workflow.compile(compileConfig);

var runnableConfig =  RunnableConfig.builder()
                .threadId("conversation-2" )
                .build();

Map<String,Object> inputs = Map.of( "messages", UserMessage.from("What's the weather like in SF currently?") );

var result = graphWithInterrupt.stream( inputs, runnableConfig );

for( var r : result ) {
  System.out.println( r.node() );
  System.out.println( r.state() );
  
}

```

    START 


    __START__
    {messages=[UserMessage { name = null contents = [TextContent { text = "What's the weather like in SF currently?" }] }]}
    agent
    {messages=[UserMessage { name = null contents = [TextContent { text = "What's the weather like in SF currently?" }] }, AiMessage { text = null toolExecutionRequests = [ToolExecutionRequest { id = "call_F1icKllQQ8a96BdkbM2yor9B", name = "execQuery", arguments = "{"query":"current weather in San Francisco"}" }] }]}


## Get State

You can fetch the latest graph checkpoint using `getState(config)`.


```java
var snapshot = graphWithInterrupt.getState(runnableConfig);
snapshot.getNext();

```




    tools



## Resume

You can resume by running the graph with a null input. The checkpoint is loaded, and with no new inputs, it will execute as if no interrupt had occurred.


```java
var result = graphWithInterrupt.stream( null, snapshot.getConfig() );

for( var r : result ) {
  log.trace( "RESULT:\n{}\n{}", r.node(), r.state() );
}
```

    RESUME REQUEST 
    RESUME FROM agent 
    execute: execQuery 
    RESULT:
    tools
    {messages=[UserMessage { name = null contents = [TextContent { text = "What's the weather like in SF currently?" }] }, AiMessage { text = null toolExecutionRequests = [ToolExecutionRequest { id = "call_F1icKllQQ8a96BdkbM2yor9B", name = "execQuery", arguments = "{"query":"current weather in San Francisco"}" }] }, ToolExecutionResultMessage { id = "call_F1icKllQQ8a96BdkbM2yor9B" toolName = "execQuery" text = "Cold, with a low of 13 degrees" }]} 
    RESULT:
    agent
    {messages=[UserMessage { name = null contents = [TextContent { text = "What's the weather like in SF currently?" }] }, AiMessage { text = null toolExecutionRequests = [ToolExecutionRequest { id = "call_F1icKllQQ8a96BdkbM2yor9B", name = "execQuery", arguments = "{"query":"current weather in San Francisco"}" }] }, ToolExecutionResultMessage { id = "call_F1icKllQQ8a96BdkbM2yor9B" toolName = "execQuery" text = "Cold, with a low of 13 degrees" }, AiMessage { text = "The current weather in San Francisco is cold, with a low of 13 degrees Celsius." toolExecutionRequests = [] }]} 
    RESULT:
    __END__
    {messages=[UserMessage { name = null contents = [TextContent { text = "What's the weather like in SF currently?" }] }, AiMessage { text = null toolExecutionRequests = [ToolExecutionRequest { id = "call_F1icKllQQ8a96BdkbM2yor9B", name = "execQuery", arguments = "{"query":"current weather in San Francisco"}" }] }, ToolExecutionResultMessage { id = "call_F1icKllQQ8a96BdkbM2yor9B" toolName = "execQuery" text = "Cold, with a low of 13 degrees" }, AiMessage { text = "The current weather in San Francisco is cold, with a low of 13 degrees Celsius." toolExecutionRequests = [] }]} 


## Check full history

Let's browse the history of this thread, from newest to oldest.




```java
RunnableConfig toReplay = null;
var states = graphWithInterrupt.getStateHistory(runnableConfig);
for( var state: states ) {
  
  log.trace( "\n---\n{}\n---",state);

  if (state.getState().messages().size() == 3) {
     toReplay = state.getConfig();
  }
}
if (toReplay==null) {
  throw new IllegalStateException("No state to replay");
}
```

    
    ---
    StateSnapshot{node=agent, state={messages=[UserMessage { name = null contents = [TextContent { text = "What's the weather like in SF currently?" }] }, AiMessage { text = null toolExecutionRequests = [ToolExecutionRequest { id = "call_F1icKllQQ8a96BdkbM2yor9B", name = "execQuery", arguments = "{"query":"current weather in San Francisco"}" }] }, ToolExecutionResultMessage { id = "call_F1icKllQQ8a96BdkbM2yor9B" toolName = "execQuery" text = "Cold, with a low of 13 degrees" }, AiMessage { text = "The current weather in San Francisco is cold, with a low of 13 degrees Celsius." toolExecutionRequests = [] }]}, config=RunnableConfig{ threadId=conversation-2, checkPointId=0305db5e-2c2c-485f-9696-83cb21ab5f64, nextNode=__END__, streamMode=VALUES }}
    --- 
    
    ---
    StateSnapshot{node=tools, state={messages=[UserMessage { name = null contents = [TextContent { text = "What's the weather like in SF currently?" }] }, AiMessage { text = null toolExecutionRequests = [ToolExecutionRequest { id = "call_F1icKllQQ8a96BdkbM2yor9B", name = "execQuery", arguments = "{"query":"current weather in San Francisco"}" }] }, ToolExecutionResultMessage { id = "call_F1icKllQQ8a96BdkbM2yor9B" toolName = "execQuery" text = "Cold, with a low of 13 degrees" }]}, config=RunnableConfig{ threadId=conversation-2, checkPointId=adf76898-e7cc-4087-8ac1-82ee247e0453, nextNode=agent, streamMode=VALUES }}
    --- 
    
    ---
    StateSnapshot{node=agent, state={messages=[UserMessage { name = null contents = [TextContent { text = "What's the weather like in SF currently?" }] }, AiMessage { text = null toolExecutionRequests = [ToolExecutionRequest { id = "call_F1icKllQQ8a96BdkbM2yor9B", name = "execQuery", arguments = "{"query":"current weather in San Francisco"}" }] }]}, config=RunnableConfig{ threadId=conversation-2, checkPointId=d8807d8e-e5fc-4383-88be-f1e883c9f76b, nextNode=tools, streamMode=VALUES }}
    --- 
    
    ---
    StateSnapshot{node=__START__, state={messages=[UserMessage { name = null contents = [TextContent { text = "What's the weather like in SF currently?" }] }]}, config=RunnableConfig{ threadId=conversation-2, checkPointId=76dbf625-2649-4bc4-b497-2387c964ec5d, nextNode=agent, streamMode=VALUES }}
    --- 


## Replay a past state

To replay from this place we just need to pass its config back to the agent.


```java
var results = graphWithInterrupt.stream(null, toReplay ); 

for( var r : results ) {
  log.trace( "RESULT:\n{}\n{}\n---", r.node(), r.state() );
}

```

    RESUME REQUEST 
    RESUME FROM tools 
    RESULT:
    agent
    {messages=[UserMessage { name = null contents = [TextContent { text = "What's the weather like in SF currently?" }] }, AiMessage { text = null toolExecutionRequests = [ToolExecutionRequest { id = "call_F1icKllQQ8a96BdkbM2yor9B", name = "execQuery", arguments = "{"query":"current weather in San Francisco"}" }] }, ToolExecutionResultMessage { id = "call_F1icKllQQ8a96BdkbM2yor9B" toolName = "execQuery" text = "Cold, with a low of 13 degrees" }, AiMessage { text = "The current weather in San Francisco is cold, with a low of 13 degrees Celsius." toolExecutionRequests = [] }]}
    --- 
    RESULT:
    __END__
    {messages=[UserMessage { name = null contents = [TextContent { text = "What's the weather like in SF currently?" }] }, AiMessage { text = null toolExecutionRequests = [ToolExecutionRequest { id = "call_F1icKllQQ8a96BdkbM2yor9B", name = "execQuery", arguments = "{"query":"current weather in San Francisco"}" }] }, ToolExecutionResultMessage { id = "call_F1icKllQQ8a96BdkbM2yor9B" toolName = "execQuery" text = "Cold, with a low of 13 degrees" }, AiMessage { text = "The current weather in San Francisco is cold, with a low of 13 degrees Celsius." toolExecutionRequests = [] }]}
    --- 

