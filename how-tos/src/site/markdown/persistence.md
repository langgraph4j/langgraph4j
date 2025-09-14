# How to add persistence ("memory") to your graph

Many AI applications need memory to share context across multiple interactions. In LangGraph4j, memory is provided for any [`StateGraph`] through [`Checkpointers`].

When creating any LangGraph workflow, you can set them up to persist their state by doing using the following:

1. A [`Checkpointer`], such as the [`MemorySaver`]
1. Pass your [`Checkpointers`] in configuration when compiling the graph.


[`StateGraph`]: https://langgraph4j.github.io/langgraph4j/apidocs/org/bsc/langgraph4j/StateGraph.html
[`Checkpointers`]: https://langgraph4j.github.io/langgraph4j/apidocs/org/bsc/langgraph4j/checkpoint/BaseCheckpointSaver.html
[`Checkpointer`]: https://langgraph4j.github.io/langgraph4j/apidocs/org/bsc/langgraph4j/checkpoint/Checkpoint.html
[`MemorySaver`]: https://langgraph4j.github.io/langgraph4j/apidocs/org/bsc/langgraph4j/checkpoint/MemorySaver.html


**Initialize Logger**


```java
try( var file = new java.io.FileInputStream("./logging.properties")) {
    java.util.logging.LogManager.getLogManager().readConfiguration( file );
}
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

public class MessageState extends MessagesState<ChatMessage> {

    public MessageState(Map<String, Object> initData) {
        super( initData  );
    }

}
```

## Register Serializers

Every object that should be stored into State **MUST BE SERIALIZABLE**. If the object is not `Serializable` by default, Langgraph4j provides a way to build and associate a custom [Serializer] to it. 

In the example, we use  [`Serializer`] for [ToolExecutionRequest] and [ChatMessage] provided by langgraph4j integration module with langchain4j .

[Serializer]: https://langgraph4j.github.io/langgraph4j/apidocs/org/bsc/langgraph4j/serializer/Serializer.html
[ChatMessage]: https://docs.langchain4j.dev/apidocs/dev/langchain4j/data/message/ChatMessage.html
[ToolExecutionRequest]: https://docs.langchain4j.dev/apidocs/dev/langchain4j/agent/tool/ToolExecutionRequest.html


```java
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.ChatMesssageSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionRequestSerializer;
import org.bsc.langgraph4j.state.AgentStateFactory;

var stateSerializer = new ObjectStreamStateSerializer<MessageState>( MessageState::new );
stateSerializer.mapper()
    // Setup custom serializer for Langchain4j ToolExecutionRequest
    .register(ToolExecutionRequest.class, new ToolExecutionRequestSerializer() )
    // Setup custom serializer for Langchain4j AiMessage
    .register(ChatMessage.class, new ChatMesssageSerializer() )

```

    SLF4J: No SLF4J providers were found.
    SLF4J: Defaulting to no-operation (NOP) logger implementation
    SLF4J: See https://www.slf4j.org/codes.html#noProviders for further details.





    SerializerMapper: 
    java.util.Map
    java.util.Collection
    dev.langchain4j.agent.tool.ToolExecutionRequest
    dev.langchain4j.data.message.ChatMessage



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
                .modelName( "gpt-4o" )
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
import dev.langchain4j.model.chat.request.ChatRequest;

var llm = new LLM();

var tools = ToolSpecifications.toolSpecificationsFrom( SearchTool.class );

var userMessage = UserMessage.from("What will the weather be like in London tomorrow?");

var request = ChatRequest.builder()
                .messages( userMessage )
                .build();

var result = model.chat(request );

result.aiMessage();
```




    AiMessage { text = "I'm unable to provide real-time weather forecasts. For the most accurate and up-to-date weather information, please check a reliable weather website or app." toolExecutionRequests = null }



## Define the graph

We can now put it all together. We will run it first without a checkpointer:



```java
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import org.bsc.langgraph4j.action.NodeAction;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.tool.DefaultToolExecutor;

// Route Message 
EdgeAction<MessageState> routeMessage = state -> {
  
  var lastMessage = state.lastMessage();
  
  if ( !lastMessage.isPresent()) return "exit";

  var message = (AiMessage)lastMessage.get();

  // If tools should be called
  if ( message.hasToolExecutionRequests() ) return "next";
  
  // If no tools are called, we can finish (respond to the user)
  return "exit";
};

// Call Model
NodeAction<MessageState> callModel = state -> {
  

  var request = ChatRequest.builder()
                .messages( state.messages() )
                .build();

  var response = model.chat(request );

  return Map.of( "messages", response.aiMessage() );
};

var searchTool = new SearchTool();


// Invoke Tool 
NodeAction<MessageState> invokeTool = state -> {

  var lastMessage = (AiMessage)state.lastMessage()
                          .orElseThrow( () -> ( new IllegalStateException( "last message not found!")) );

  var executionRequest = lastMessage.toolExecutionRequests().get(0);

  var executor = new DefaultToolExecutor( searchTool, executionRequest );

  var result = executor.execute( executionRequest, null );

  return Map.of( "messages", AiMessage.from( result ) );
};

// Define Graph

var workflow = new StateGraph<MessageState> ( MessageState.SCHEMA, stateSerializer )
  .addNode("agent", node_async(callModel) )
  .addNode("tools", node_async(invokeTool) )
  .addEdge(START, "agent")
  .addConditionalEdges("agent", edge_async(routeMessage), Map.of( "next", "tools", "exit", END ))
  .addEdge("tools", "agent");

var graph = workflow.compile();
```


```java

Map<String,Object> inputs = Map.of( "messages", AiMessage.from("Hi I'm Bartolo, niced to meet you.") );

var result = graph.stream( inputs );

for( var r : result ) {
  System.out.println( r.node() );
  if( r.node().equals("agent")) {
    System.out.println( r.state() );
  }
}
```

    __START__
    agent
    {messages=[AiMessage { text = "Hi I'm Bartolo, niced to meet you." toolExecutionRequests = null }, AiMessage { text = "Hello Bartolo, nice to meet you too! How can I assist you today?" toolExecutionRequests = null }]}
    __END__



```java

Map<String,Object> inputs = Map.of( "messages", AiMessage.from("Remember my name?") );

var result = graph.stream( inputs );

for( var r : result ) {
  System.out.println( r.node() );
  if( r.node().equals("agent")) {
    System.out.println( r.state() );
  }
}
```

    __START__
    agent
    {messages=[AiMessage { text = "Remember my name?" toolExecutionRequests = null }, AiMessage { text = "I'm sorry, but I don't have the ability to remember personal information or previous interactions. How can I assist you today?" toolExecutionRequests = null }]}
    __END__


## Add Memory

Let's try it again with a checkpointer. We will use the
[`MemorySaver`],
which will "save" checkpoints in-memory.

[`MemorySaver`]: https://langgraph4j.github.io/langgraph4j/apidocs/org/bsc/langgraph4j/checkpoint/MemorySaver.html


```java
import org.bsc.langgraph4j.checkpoint.MemorySaver; 
import org.bsc.langgraph4j.CompileConfig; 

// Here we only save in-memory
var memory = new MemorySaver();

var compileConfig = CompileConfig.builder()
                    .checkpointSaver(memory)
                    .build();

var persistentGraph = workflow.compile( compileConfig );
```


```java
import org.bsc.langgraph4j.RunnableConfig;

var runnableConfig =  RunnableConfig.builder()
                .threadId("conversation-num-1" )
                .build();

Map<String,Object> inputs = Map.of( "messages", AiMessage.from("Hi I'm Bartolo, niced to meet you.") );

var result = persistentGraph.stream( inputs, runnableConfig );

for( var r : result ) {
  System.out.println( r.node() );
  if( r.node().equals("agent")) {
    System.out.println( r.state().lastMessage().orElse(null) );
  }
}
```

    __START__
    agent
    AiMessage { text = "Hello Bartolo, nice to meet you too! How can I assist you today?" toolExecutionRequests = null }
    __END__



```java

Map<String,Object> inputs = Map.of( "messages", AiMessage.from("Remember my name?") );

var result = persistentGraph.stream( inputs, runnableConfig );

for( var r : result ) {
  System.out.println( r.node() );
  if( r.node().equals("agent")) {
    System.out.println( r.state().lastMessage().orElse(null) );
  }
}
```

    __START__
    agent
    AiMessage { text = "I'm sorry, but I can't remember personal information like names between interactions. However, I'm here to help with any questions or information you need!" toolExecutionRequests = null }
    __END__


## New Conversational Thread

If we want to start a new conversation, we can pass in a different
**`thread_id`**. Poof! All the memories are gone (just kidding, they'll always
live in that other thread)!



```java
runnableConfig =  RunnableConfig.builder()
                .threadId("conversation-2" )
                .build();
```


```java
inputs = Map.of( "messages", AiMessage.from("you know my name?") );

var result = persistentGraph.stream( inputs, runnableConfig );

for( var r : result ) {
  System.out.println( r.node() );
  if( r.node().equals("agent")) {
    System.out.println( r.state().lastMessage().orElse(null) );
  }
}
```

    __START__
    agent
    AiMessage { text = "No, I don't know your name. I don't have access to personal data about users unless it has been shared with me in the course of our conversation. If you have any questions or need assistance, feel free to ask!" toolExecutionRequests = null }
    __END__

