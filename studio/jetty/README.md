## Langgraph4j Studio - Jetty reference implementation

This module provides the class `LangGraphStudioServer4Jetty` that provides basic implementation of LangGraph4j Studio for the [Jetty] server.

### Add dependency

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-studio-jetty</artifactId>
    <version>1.6.3</version>
</dependency>
```

### Create a workflow and connect it to the playground webapp

```java

public static void main(String[] args) throws Exception {

    StateGraph<AgentState> workflow = createWorkflow(); 

    // define your workflow

    var saver = new MemorySaver();

    var instance = LangGraphStudioServer.Instance.builder()
            .title("LangGraph4j Studio")
            .addInputStringArg("input")
            .graph(workflow)
            .compileConfig(CompileConfig.builder()
                    .checkpointSaver(saver)
                    .build())
            .build();

    // connect playground webapp to workflow
    LangGraphStudioServer4Jetty.builder()
            .port(8080)
            .instance("default", instance)
            .build()
            .start()
            .join();
}
```

### Open Studio

To run LangGraph4j Studio application, open browser and navigate to:

```
http://localhost:<port>/?instance=<instance_id>
```

where `<port>` is the port you specified in the builder (8080 in the example above) and `<instance_id>` is the instance id you specified in the builder ("default" in the example above).


[Jetty]: https://www.eclipse.org/jetty/