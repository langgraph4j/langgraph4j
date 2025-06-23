# langgraph4j-postgres-saver

**Persist and manage your [langgraph4j](https://github.com/langgraph4j/langgraph4j) workflow state in a PostgreSQL database for its durability**

## Overview

`langgraph4j-postgres-saver` is a module for the [langgraph4j](https://github.com/langgraph4j/langgraph4j) ecosystem that enables persistent, reliable storage of workflow state in a PostgreSQL database. This makes your LLM-based applications stateful across executions—ensuring that workflow progress is not lost and can be resumed or analyzed at any point.

Key features include:
- **PostgreSQL-backed persistence:** All workflow states are stored in a PostgreSQL database, surviving process restarts or system failures.
- **State caching:** In-memory cache for state data to optimize performance by minimizing database round-trips during workflow execution.
- **Schema provisioning:** Built-in services to easily create the required database schema for storing workflow states.

## Features

- **Durable State:** Persist the entire state of your langgraph4j workflow, allowing continuation or recovery at any time.
- **Performance Caching:** Automatic in-memory caching reduces load on the database and accelerates repeated workflow invocations.
- **Easy Schema Initialization:** Helper services are provided to create the required tables and structures in your PostgreSQL instance.
- **Seamless Integration:** Works out of the box with langgraph4j’s state management and workflow APIs.

## Requirements

- **PostgreSQL Database:** Version 16.4 or higher recommended.
- **Java 17+**
- **langgraph4j core library**

## Getting Started

### Add Dependency

Add the following to your project's build configuration:

**Maven**
```xml
<dependency>
    <groupId>langgraph4j</groupId>
    <artifactId>langgraph4j-postgres-saver</artifactId>
    <version>1.6.0-beta4</version>
</dependency>
```

**Gradle**
```gradle
implementation 'langgraph4j:langgraph4j-postgres-saver:1.6.0-beta4'
```

### Initialize the PostgresSaver

The PostgresSaver is configured using a builder pattern. You need to provide your database connection parameters and a state serializer.

```java
var saver = PostgresSaver saver = PostgresSaver.builder()
    .host("localhost")
    .port(5432)
    .user("the user name")
    .password("your password")
    .database("database name")
    .stateSerializer( stateSerializer )
    .dropTablesFirst( true | false ) // true try to drop table first. default is false
    .createTables( true | false ) // create tables if don't exist. default is false except if dropTablesFirst = true
```

### Example Usage

Below is a complete example of how to use langgraph4j-postgres-saver to persist, reload, and verify workflow state:

```java
public void testCheckpointWithNotReleasedThread() throws Exception {
    
    var stateSerializer = new ObjectStreamStateSerializer<>( AgentState::new );

    // Init Checkpoints saver
    var saver = PostgresSaver.builder()
            .host("localhost")
            .port(5432)
            .user("admin")
            .password("bsorrentino")
            .database("lg4j-store")
            .stateSerializer( stateSerializer )
            .createTables( true )
            .build();

    NodeAction<AgentState> agent_1 = state -> {
        log.info( "agent_1");
        return Map.of("agent_1:prop1", "agent_1:test");
    };

    var graph = new StateGraph<>(AgentState::new)
            .addNode("agent_1", node_async( agent_1 ))
            .addEdge( START,"agent_1")
            .addEdge( "agent_1",  END)
            ;

    var compileConfig = CompileConfig.builder()
            .checkpointSaver(saver)
            .releaseThread(false)
            .build();

    var runnableConfig = RunnableConfig.builder().build();
    var workflow = graph.compile( compileConfig );

    Map<String, Object> inputs = Map.of( "input", "test1");

    var result = workflow.invoke( inputs, runnableConfig );

    // Get checkpoint history
    var history = workflow.getStateHistory( runnableConfig ); 

    assertFalse( history.isEmpty() );
    assertEquals( 2, history.size() );
    
    // Get last saved checkpoint
    var lastSnapshot = workflow.lastStateOf( runnableConfig );

    assertTrue( lastSnapshot.isPresent() );
    assertEquals( "agent_1", lastSnapshot.get().node() );
    assertEquals( END, lastSnapshot.get().next() );

    // Test checkpoints reloading from database

    // Create a new saver (reset cache)
    saver =  PostgresSaver.builder()
            .host("localhost")
            .port(5432)
            .user("admin")
            .password("bsorrentino")
            .database("lg4j-store")
            .stateSerializer( stateSerializer )
            .build();

    compileConfig = CompileConfig.builder()
            .checkpointSaver(saver)
            .releaseThread(false)
            .build();

    runnableConfig = RunnableConfig.builder().build();
    workflow = graph.compile( compileConfig );

    history = workflow.getStateHistory( runnableConfig );

    assertFalse( history.isEmpty() );
    assertEquals( 2, history.size() );

    lastSnapshot = workflow.lastStateOf( runnableConfig );

    assertTrue( lastSnapshot.isPresent() );
    assertEquals( "agent_1", lastSnapshot.get().node() );
    assertEquals( END, lastSnapshot.get().next() );

    saver.release( runnableConfig );
}
```
