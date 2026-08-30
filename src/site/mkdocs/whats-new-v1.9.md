# Migrating from LangGraph4j 1.8.x to 1.9

This guide describes all the new features and the refactored ones from 1.8.x  that will delivered in 1.9 release with indication about usage and possible breaking changes.


## Refactor internal streaming engine to support event emitting

This new feature rely on the `AsyncGeneratorFlow` from [async-generator 5.0](https://github.com/bsorrentino/java-async-generator). This model is closer to Java Reactive Stream and will guarantee, 
in the near future (2.0),  the support to [Reactor Flow](https://projectreactor.io) and the possibility to emit custom event during graph execution as requinder in [#402](https://github.com/langgraph4j/langgraph4j/issues/402)

Core graph execution and the Spring AI/LangChain4j streaming generators now use the new `AsyncGeneratorFlow`.

### Compatibility

Normal iteration through `stream()` remains source-compatible, but code that subclasses streaming
generators, supplies a `BlockingQueue`, or depends on `AsyncGenerator.WithResult` must migrate.

## Release thread by default

With a checkpoint saver configured, a completed run now releases its active
thread automatically remains consistent across graph executions. Releasing archives/tags the checkpoints according to the saver implementation and removes the active checkpoint set. This affects code that expects to inspect, resume, replay, or manually release the same active thread after completion.
The thread is not released in these cases:
* **on graph interruption** 
  > This preserves the behavior from previous releases and supports HITL (Human In The Loop) flows.
* **on graph exception**
  > When the graph raises an exception, the thread is not released. This allows the graph caller to choose the release strategy that matches its fault and recovery implementation.
  > ```java 
  >  CheckpointSaver saver = ....
  >  RunnableConfig config = ....
  >  try {
  >     graph.stream();
  >  
  >  }
  >  catch( Exception ex ) {
  >     saver.releaseThread(config);
  >  }
  > ```

### Compatibility

⚠️ **BREAKING CHANGE**:
Only if, **after the graph completes normally**, you update the graph state and run the graph again, in that case, use `CompileConfig.builder().releaseThread(false)` to keep backward compatibility. 

**❌ Doesn't work**
```java
  CheckpointSaver saver = new MemorySaver();
  CompileConfig compileConfig = CompileConfig.builder()
                                    .checkpointSaver(saver)
                                    .build();
  RunnableConfig config = RunnableConfig.build()
                              .threadId("T1")
                              .build();
  
  graph.invoke( .... );

  graph.updateState( config, Map.of(...) );

  graph.invoke( .... );
```

**✅ works**
```java
  CheckpointSaver saver = new MemorySaver();
  CompileConfig compileConfig = CompileConfig.builder()
                                    .releaseThread(false)
                                    .checkpointSaver(saver)
                                    .build();
  RunnableConfig config = RunnableConfig.build()
                              .threadId("T1")
                              .build();

  graph.invoke( .... );

  graph.updateState( config, Map.of(...) );

  graph.invoke( .... );

```

⚠️ Note that `graph.updateState()` works as before for graph interruptions and graph exceptions.


## Checkpoint Saver upgrade

### Versioning (experimental)
In this version we have added a first experimental tag versioning support. Its implementation is optional so We have kept backward compatibility.

The `BaseCheckpointSaver.Tag` is a java record  with the following fields:

- `threadId()`
- `version()`, returning `Optional<Integer>`
- `checkpoints()`
- `lastCheckpoint()`

The `BaseCheckpointSaver` adds new methos for query tags:

```java
// return tag that match the config.threadId and (otionally) the version
Optional<Tag> tag(RunnableConfig config, @Nullable Integer version) throws Exception;

// Returns the last independent tag whether it is versioned or not
Optional<Tag> lastTag(RunnableConfig config) throws Exception;
```

The `MemorySaver` and `FileSystemSaver` now retain released runs as numbered tags.
`release(...)` returns the created version, and `lastTag(...)` retrieves the
most recent released run.


The `GraphResult.asLastCheckpointStateData()` now uses `Tag.lastCheckpoint()`; so its
observable result is unchanged.

### Checkpoint savers and subgraphs

Considering the increasingly important role that subgraphs are playing, we have added the functionality of registering the subgraph saver to `BaseCheckpointSaver` via the methods:

```java
void putSubGraphSaver(
    RunnableConfig parentConfig,
    RunnableConfig subGraphConfig,
    BaseCheckpointSaver subGraphSaver);

Collection<SubGraphSaver> listSubGraphSaver(RunnableConfig parentConfig);
```

This allows a parent saver to release checkpoint savers created by compiled subgraphs. `SubCompiledGraphNodeAction` registers the subgraph saver with the parent saver, and `AbstractCheckpointSaver.release(...)` cascades release to those subgraph threads.

The `AbstractCheckpointSaver` already provide a reference implementations for subgraphs registration.

### Error and interruption handling

Checkpoint savers now expose explicit hooks for interrupted and failed executions:

```java
void registerInterruption(RunnableConfig config, InterruptionMetadata interruptionMetadata) throws Exception;

Tag releaseCheckpointsOnError(RunnableConfig config, Throwable error) throws Exception;
```

To improve the graph running post analysis very important in production environment we added
the possibility to preserve the reason for an interruption and distinguish normal completion from an error release. `InterruptionMetadata` now exposes also the interruption reason.

The **in-memory**, **file-system**, **Redis**, **Postgres**, **Oracle**, **MySQL**, **CockroachDB**, **DynamoDB**, and **Hazelcast** savers have been aligned with this contract providing a default implementation.


### SQLite saver upgrade (V2)

The SQLite module now includes a `SQLiteSaverV2` implementation backed by versioned SQL resources.
`SQLiteSaverV2` is aligned with the new checkpoint saver **release/error/interruption** contract, while the existing `SQLiteSaver` remains available for the V1 schema.

### PostgreSQL saver upgrade (V2)

The PostgreSQL module now includes a `PostgresSaverV2` implementation backed by versioned SQL resources.
`PostgresSaverV2` is aligned with the new checkpoint saver **release/error/interruption** contract, while the existing `PostgresSaver` remains available for the V1 schema.

## Starting, resuming, and invoking graphs

`GraphInput` is now the preferred and explicit execution API.

Before:
```java
graph.stream(Map.of("messages", message), config);
graph.stream(null, config); // resume
graph.invoke(Map.of("messages", message), config);
```

After:
```java
graph.stream(GraphInput.args(Map.of("messages", message)), config);
graph.stream(GraphInput.resume(), config);
graph.stream(GraphInput.resume(Map.of("approval", "APPROVED")), config);
graph.invoke(GraphInput.args(Map.of("messages", message)), config);
```

Use `GraphInput.noArgs()` to start a graph with no arguments. Do not use a null
map to communicate whether a run is new or resumed.

The following overloads remain available in 1.9 but are deprecated for removal:

- `stream(Map<String,Object>, RunnableConfig)`
- `stream(Map<String,Object>)`
- `streamSnapshots(Map<String,Object>, RunnableConfig)`
- `invoke(Map<String,Object>, RunnableConfig)`
- `invoke(Map<String,Object>)`

`RunnableConfig.empty()` is a reusable empty configuration for executions that
do not need a thread ID, checkpoint ID, metadata, or custom stream mode.

## Runtime diagnostics and metadata

`GraphRunnerException` now carries more execution context:

- `config()` returns the `RunnableConfig` associated with the failed run.
- `nodeId()` returns the failing node id when it is available.

`RunnableConfig` also tracks the current graph node path through `nodePath()`. This replaces the older partial `graphPath()` usage and is available for regular nodes as well as subgraph execution.


## State cloning and transient attributes

### Optional state-clone bypass

Graph outputs and checkpoint snapshots are cloned by default, as in 1.8. For a
performance-sensitive run, cloning can now be disabled:

```java
var config = RunnableConfig.builder()
    .disableCloneState()
    .build();
```

When disabled, state objects exposed outside the graph are not guaranteed to
be immutable and can reflect later mutations. Only enable this after confirming
that callers do not retain or mutate emitted state.

`RunnableConfig.isCloneStateDisabled()` reports the effective option.

**👉 Note**
> Since clone process involve also the serialization process, this implies that custom class stored inside state must be made serializable so, the disable state cloning has been thought to avoid this, at least in the first development phase when you don't need to persist state yet.


### Transient state attributes

`StateSerializer` adds:
```java
StateSerializer.declareTransientAttributes( String... attributes) {
```

Declared attributes are omitted from serialized bytes/text and restored from
the serializer's in-memory transient data when deserializing in the same
process. Support is implemented by `ObjectStreamStateSerializer`,
`JacksonStateSerializer`, and `GsonStateSerializer`.

Transient values are process-local. They are not available after restart, on a
different node, or from a newly created serializer instance. Do not use them
for data required to resume a persisted graph.

Keep in mind that `GsonStateSerializer` is deprecated for removal since 1.9; migrate to `JacksonStateSerializer` or an integration-specific Jackson serializer.


## Agent framework changes

### Skills API (experimental)

Core adds a small provider-neutral skills API:

- `SkillSource` supplies skill Markdown.
- `SkillPath` reads a file-backed skill directory.
- `SkillParser` parses YAML-like front matter and content.
- `SkillParser.FrontMatter` exposes string and string-list values.

These APIs are also used by the new Spring AI skilled sub-agent support (see below).

### Core `AgentEx`

`AgentEx` introduces `ToolBehaviour<M, State>`. A tool behavior supplies its name and can add its execution node to the state graph. Consequently, the low-level `AgentEx.Builder.build(...)` method now accepts tool behaviors rather
than raw tool objects plus a separate `toolName`/`executeToolFactory` mapping.


## Spring AI integration

### Reusable sub-agent types:

We have introduced the sub agent abstraction  providing a concrete skill based implementation through the following classes:
- `SubAgent` exposes a compiled agent as both a `ToolCallback` and an `AgentEx.ToolBehaviour`.
- `CustomSubAgent` wraps an explicitly supplied compiled graph.
- `SkilledReactSubAgent` builds a sub-agent from a `SkillSource`.
- `SkillResource` adapts a Spring `Resource` to `SkillSource`.

Take a look to this article for further details: [Skill-Based Sub-Agents with LangGraph4j and Spring AI](https://bsorrentino.github.io/bsorrentino/ai/2026/04/28/LangGraph4j-SubAgent.html)

⚠️ **BREAKING CHANGE**: 
> The `spring-ai-agent-utils` dependency in the Spring AI agent module has been moved to test scope, its skill implementation has been removed by Spring AI module. Applications that used it transitively must declare it directly.

### Agent migration

The former `org.bsc.langgraph4j.spring.ai.agent.ReactAgent` interface was removed. 
Its responsibilities are split into:

- `BaseReactAgentBuilder` for model, serializer, tools, schema, streaming, and conversation-context configuration.
- `ReactAgentBuilder` for the compact `AgentExecutor` graph.
- `ReactAgentBuilderEx` for `AgentExecutorEx`, approvals, node/edge hooks, and sub-agents.
- top-level `ChatService` and the default implementation.

## LangChain4j integration

### Default serializer

The default serializer for the LangChain4j `AgentExecutorEx` changes from the standard Java object serializer to its JSON serializer. Persisted checkpoints must be read with a serializer compatible with the format that created them;
do not mix old binary checkpoint files with the new default.

### Message attribute serialization

The standard LangChain4j serializers now preserve attributes on:

- `UserMessage`
- `ToolExecutionResultMessage`

This matters for applications that attach provider-specific metadata or tool execution details to LangChain4j messages and then persist graph state through LangGraph4j serializers.

### Removal

The deprecated LangChain4j `LLMStreamingGenerator` class has been removed. Use
`org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator`.

## New JSON graph DSL module

The new `langgraph4j-dsl` module has been added to the project. This module provides the `JsonDslGenerator` class 
that is a `GraphDefinition.Reducer` that exports a graph, including parallel nodes and nested subgraphs, to a JSON representation:

```java
String json = compiledGraph.reduce(new JsonDslGenerator<>());
```

Such representation will be used to deep refactor **Studio** Web UI to [React-Flow](https://reactflow.dev) 

Artifact is already included in the BOM:

```xml
<dependency>
  <groupId>org.bsc.langgraph4j</groupId>
  <artifactId>langgraph4j-dsl</artifactId>
</dependency>
```


## Javelit integration

* Replace the removed `SpinnerComponent` with `JtSpinner`.


##  Other Core API removals, renames, and behavior changes

### Runtime configuration 

* `RunnableConfig` remains immutable from the user's perspective. Methods such as `updateMetadata(...)` and 
`removeMetadata(...)` return a new configuration. Metadata builders reject null values.
* Replace `RunnableConfig.graphPath()` with `nodePath()` where the code needs the current node path.
> While `graphPath()` returns a partial path information only when subgraphs are involved the `nodePath()` return a complete path information also when no subgraphs are involved.

### Streaming end marker

* `StreamingOutputEnd.isEnd()` is no longer overridden. Use `isStreamingEnd()` to detect the end-of-stream marker. `isEnd()` retains the base `NodeOutput` meaning and must not be used as a streaming-end test.


## Repository and documentation changes

These changes affect project contributors or documentation deployment rather than library migration:

- Maven/site documentation was reorganized and old generated Spring AI pages
  were removed in favor of maintained module READMEs.
- MkDocs gained Mike-based documentation versioning.
- A `SECURITY.md` policy was added with supported-version and vulnerability-reporting guidance.
- GitHub Actions and snapshot/site deployment configuration were updated.


##  Dependencies and coordinates

Change the LangGraph4j version in your BOM or individual dependencies:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.bsc.langgraph4j</groupId>
      <artifactId>langgraph4j-bom</artifactId>
      <version>1.9.0-beta4</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```





