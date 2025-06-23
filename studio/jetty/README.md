## Langgraph4j Studio - Jetty reference implementation


### Add dependency

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-studio-jetty</artifactId>
    <version>1.6.0-beta4</version>
</dependency>
```

### Create a workflow and connect it to the playground webapp

```java
public static void main(String[] args) throws Exception {


    var workflow = new StateGraph<>(AgentState::new);

    // define your workflow   


    var saver = new MemorySaver();
    // connect playgroud webapp to workflow
    var server = LangGraphStreamingServerJetty.builder()
                    .port(8080)
                    .title("LANGGRAPH4j - TEST")
                    .stateGraph(workflow)
                    .checkpointSaver(saver)
                    .addInputStringArg("input")
                    .build();
    // start playground
    server.start().join();
}

```
