# Migrating from LangGraph4j 1.8.x to 1.9

This guide describes the user-visible differences between `develop` at
`eba30913aa58` (1.8.19) and `feature/#386_ver_1.9` at `694def9e06ee`.
Repository-only changes such as tests, CI workflows, generated site files, and
formatting are summarized separately at the end.

## Migration checklist

Before upgrading, check the following items:

1. Replace calls to `CompiledGraph.stream(Map, ...)`,
   `streamSnapshots(Map, ...)`, and `invoke(Map, ...)` with their `GraphInput`
   equivalents. The map overloads are deprecated for removal.
2. If a checkpoint must remain active after a run, explicitly configure
   `CompileConfig.builder().releaseThread(false)`. The default is now `true`.
3. Replace `CompiledGraph.setMaxIterations(...)` with
   `CompileConfig.builder().recursionLimit(...)`.
4. Replace `CompiledGraph.RunnableErrors` with `CompiledGraph.RunErrors` if the
   enum was referenced directly.
5. Replace `VersionedMemorySaver` and `HasVersions`; versioned released runs are
   now accessed through `BaseCheckpointSaver.Tag`, `tag(...)`, and `lastTag(...)`.
6. Update custom `BaseCheckpointSaver` implementations for the expanded saver
   contract, preferably by extending `AbstractCheckpointSaver`.
7. Replace `RunnableConfig.graphPath()` with `nodePath()` where the code needs
   the current node path.
8. Replace `GsonStateSerializer`; it is deprecated for removal. Prefer a
   Jackson-based serializer.
9. If using LangChain4j or Spring AI streaming classes directly, migrate from
   the async-generator 4.x queue API to async-generator 5.x flow semantics.
10. If using the Spring AI agent builders, migrate away from the removed
    `ReactAgent` type and custom build-time chat-service factory.
11. If using `javelit`, replace the removed `SpinnerComponent`.

## 1. Dependencies and coordinates

Change the LangGraph4j version in your BOM or individual dependencies:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.bsc.langgraph4j</groupId>
      <artifactId>langgraph4j-bom</artifactId>
      <version>${langgraph4j.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Set `langgraph4j.version` to the published 1.9 release. The branch currently
uses `1.9-SNAPSHOT`.

The relevant dependency baseline is:

| Dependency | 1.8.19 | 1.9 |
| --- | --- | --- |
| Java | 17+ | 17+ |
| async-generator | 4.3.1 | 5.0.0 |
| LangChain4j | 1.16.2 / beta26 | unchanged |
| Spring AI | 2.0.0 | unchanged |
| Jackson Databind used by core | 2.21.1 | unchanged |

`langchain4j-skills` now uses the common `${langchain4j.beta}` version instead
of the previously hard-coded `1.12.1-beta21`. Applications that override
LangChain4j dependencies should keep all LangChain4j beta artifacts aligned.

The `spring-ai-agent-utils` dependency in the Spring AI agent module is now
test-scoped. Applications that used it transitively must declare it directly.

## 2. Starting, resuming, and invoking graphs

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

The following overloads remain available in 1.9 but are deprecated for
removal:

- `stream(Map<String,Object>, RunnableConfig)`
- `stream(Map<String,Object>)`
- `streamSnapshots(Map<String,Object>, RunnableConfig)`
- `invoke(Map<String,Object>, RunnableConfig)`
- `invoke(Map<String,Object>)`

`RunnableConfig.empty()` is a reusable empty configuration for executions that
do not need a thread ID, checkpoint ID, metadata, or custom stream mode.

## 3. Checkpoint lifecycle now releases by default

`CompileConfig.releaseThread` changed from `false` to `true`.

With a checkpoint saver configured, a completed run now releases its active
thread automatically. Releasing archives/tags the checkpoints according to the
saver implementation and removes the active checkpoint set. This affects code
that expects to inspect, resume, replay, or manually release the same active
thread after completion.

Preserve the 1.8 behavior explicitly when needed:

```java
var compileConfig = CompileConfig.builder()
    .checkpointSaver(saver)
    .releaseThread(false)
    .build();
```

Use `releaseThread(false)` for long-lived conversations, time travel, manual
checkpoint lifecycle management, or any flow that resumes after a normally
completed invocation. Interrupted runs remain resumable because execution has
not reached normal completion.

An initial non-resume invocation now consults the saver for existing thread
state regardless of `releaseThread`. Use a new thread ID when a genuinely fresh
run is required, or release/delete the prior active state first.

## 4. Saver versioning and tags

### Unified tag API

`BaseCheckpointSaver.Tag` changed from a two-field record into an immutable
class with:

- `threadId()`
- `version()`, returning `Optional<Integer>`
- `checkpoints()`
- `lastCheckpoint()`

The old two-argument constructor remains available, but record-specific
behavior such as record pattern matching or assumptions about generated
`equals`, `hashCode`, and `toString` no longer applies.

`BaseCheckpointSaver` adds:

```java
Optional<Tag> tag(RunnableConfig config, Integer version) throws Exception;
Optional<Tag> lastTag(RunnableConfig config) throws Exception;
```

`MemorySaver` and `FileSystemSaver` now retain released runs as numbered tags.
`release(...)` returns the created version, and `lastTag(...)` retrieves the
most recent released run.

```java
var released = saver.release(config);
var version = released.version().orElseThrow();

var lastRun = saver.lastTag(config).orElseThrow();
var finalCheckpoint = lastRun.lastCheckpoint().orElseThrow();
```

`GraphResult.asLastCheckpointStateData()` now uses `Tag.lastCheckpoint()`; its
observable result is unchanged.

### Removed versioning types

`VersionedMemorySaver` and `HasVersions` were removed. Replace
`versionsByThreadId(...)` and `lastVersionByThreadId(...)` usage with retained
`Tag` objects and the unified `tag(...)`/`lastTag(...)` API on `MemorySaver`.

### FileSystemSaver

Released files continue to use versioned names such as
`thread-<threadId>-v<version>-saver.json` or `.bin`.

The new helper below lists versioned saver files and returns an empty stream if
the directory does not exist or is not a directory:

```java
FileSystemSaver.list(folder, (threadId, version) -> true);
```

`deleteFile(...)` still exists, but `release(...)` is the normal lifecycle API
when the run should be retained as a version.

### Custom checkpoint savers and subgraphs

`BaseCheckpointSaver` also adds subgraph-saver registration:

```java
void putSubGraphSaver(
    RunnableConfig parentConfig,
    RunnableConfig subGraphConfig,
    BaseCheckpointSaver subGraphSaver);

Collection<SubGraphSaver> listSubGraphSaver(RunnableConfig parentConfig);
```

This allows a parent saver to release checkpoint savers created by compiled
subgraphs. `SubCompiledGraphNodeAction` registers the subgraph saver with the
parent saver, and `AbstractCheckpointSaver.release(...)` cascades release to
those subgraph threads.

Custom implementations that directly implement `BaseCheckpointSaver` must
implement the new methods. Extending `AbstractCheckpointSaver` supplies the
default tag behavior and subgraph bookkeeping.

## 5. State cloning and transient attributes

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

### Transient state attributes

`StateSerializer` adds:

```java
serializer.declareTransientAttributes("runtimeClient", "openStream");
```

Declared attributes are omitted from serialized bytes/text and restored from
the serializer's in-memory transient data when deserializing in the same
process. Support is implemented by `ObjectStreamStateSerializer`,
`JacksonStateSerializer`, and `GsonStateSerializer`.

Transient values are process-local. They are not available after restart, on a
different node, or from a newly created serializer instance. Do not use them
for data required to resume a persisted graph.

`GsonStateSerializer` is deprecated for removal since 1.9; migrate to
`JacksonStateSerializer` or an integration-specific Jackson serializer.

`TypeMapper.Reference` now accepts a `Class<T>` and uses its fully qualified
class name:

```java
var reference = new TypeMapper.Reference<MyType>(MyType.class) {};
```

## 6. Runtime configuration and graph metadata

`RunnableConfig` remains immutable from the user's perspective. Methods such
as `updateMetadata(...)`, `removeMetadata(...)`, `withCheckPointId(...)`, and
`withStreamMode(...)` return a new configuration. Metadata builders reject null
values.

The graph now records a node hierarchy separately from the graph hierarchy:

- `RunnableConfig.nodePath()` and `GRAPH_NODE_PATH` are new.
- `RunnableConfig.graphPath()` is deprecated since 1.9.
- `GraphPath.replaceLast(...)` creates a path with its final element replaced.
- `GraphDefinition.Nodes.isSubgraphNode(...)` and `hasSubGraphs()` expose
  subgraph checks.

Code in hooks or custom subgraph integrations should use `nodePath()` to locate
the active node. Resume metadata for a subgraph is automatically removed after
the resumed embedded generator completes, preventing stale resume data from
leaking into later nodes.

## 7. Core API removals, renames, and behavior changes

### Recursion limit

`CompiledGraph.setMaxIterations(int)` was removed. Configure the limit before
compilation:

```java
var graph = workflow.compile(CompileConfig.builder()
    .recursionLimit(100)
    .build());
```

### Error enum and execution exceptions

`CompiledGraph.RunnableErrors` was renamed to `CompiledGraph.RunErrors`.

Graph execution failures continue to use `GraphRunnerException` and retain the
associated `RunnableConfig`. `GraphRunnerException.of(Throwable)` is a new
helper for locating a graph runner exception in a cause chain.

### Streaming end marker

`StreamingOutputEnd.isEnd()` is no longer overridden. Use
`isStreamingEnd()` to detect the end-of-stream marker. `isEnd()` retains the
base `NodeOutput` meaning and must not be used as a streaming-end test.

### Hook and exception utility extensions

Applications that subclass `WrapCallHookSubgraphAware` must update overrides of
`isSubgraphRequested(...)` and `isSubgraphEnded(...)`: both now return
`Optional<String>` containing a node ID instead of `Optional<Step>`. The helper
tracks nested subgraphs by node ID rather than by the deprecated graph path.

`HookCalls.callListAsStream()` and `callMapAsStream(String)` are now public, and
the new no-argument `callMapAsStream()` exposes the keyed hook entries. Although
`HookCalls` is in an internal package, this affects extensions that used it.

`ExceptionUtils.findCauseByType(...)` now preserves the requested exception
type in its return value (`Optional<Ex>` instead of `Optional<Throwable>`), so
most callers can remove an explicit cast.

### Internal streaming engine

Core graph execution and the Spring AI/LangChain4j streaming generators now use
`AsyncGeneratorFlow` from async-generator 5.0. Normal iteration through
`stream()` remains source-compatible, but code that subclasses streaming
generators, supplies a `BlockingQueue`, or depends on
`AsyncGenerator.WithResult` must migrate.

`StreamingChatGenerator` now implements `AsyncGenerator`,
`AsyncGenerator.HasResultValue`, and `LG4JLoggable` directly. Its builder no
longer accepts `queue(...)`.

The deprecated LangChain4j `LLMStreamingGenerator` class was removed. Use
`org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator`.

## 8. Agent framework changes

### Core `AgentEx`

`AgentEx` introduces `ToolBehaviour<M, State>`. A tool behavior supplies its
name and can add its execution node to the state graph. Consequently, the
low-level `AgentEx.Builder.build(...)` method now accepts tool behaviors rather
than raw tool objects plus a separate `toolName`/`executeToolFactory` mapping.

Hook registration is consolidated:

- Use `addNodeHook(...)`, optionally with a node ID, for before/wrap/after node
  hooks.
- Use `addEdgeHook(...)`, optionally with a node ID, for before/wrap/after edge
  hooks.
- The specialized low-level methods such as `addCallModelHook`,
  `addDispatchToolsHook`, `addShouldContinueHook`, `addDispatchActionHook`, and
  `addApprovalActionHook` were removed from `AgentEx.Builder`.

The higher-level Spring AI builders retain compatibility convenience methods
with those specialized names.

### Skills API

Core adds a small provider-neutral skills API:

- `SkillSource` supplies skill Markdown.
- `SkillPath` reads a file-backed skill directory.
- `SkillParser` parses YAML-like front matter and content.
- `SkillParser.FrontMatter` exposes string and string-list values.

These APIs are also used by the new Spring AI skilled sub-agent support.

## 9. Spring AI agent migration

The former `org.bsc.langgraph4j.spring.ai.agent.ReactAgent` interface was
removed. Its responsibilities are split into:

- `BaseReactAgentBuilder` for model, serializer, tools, schema, streaming, and
  conversation-context configuration.
- `ReactAgentBuilder` for the compact `AgentExecutor` graph.
- `ReactAgentBuilderEx` for `AgentExecutorEx`, approvals, node/edge hooks, and
  sub-agents.
- top-level `ChatService` and the default implementation.

Most applications that only call `AgentExecutor.builder()` or
`AgentExecutorEx.builder()` can keep their fluent configuration. The important
change affects custom chat services.

Before:

```java
var graph = AgentExecutor.builder()
    .chatModel(chatModel)
    .build(builder -> new MyChatService(builder));
```

After:

```java
var graph = AgentExecutor.builder()
    .chatModel(chatModel)
    .chatService(myChatService)
    .build();
```

Implement the new top-level
`org.bsc.langgraph4j.spring.ai.agent.ChatService`, not
`ReactAgent.ChatService`.

`AgentExecutorEx.Builder` also adds a direct compile overload:

```java
CompiledGraph<AgentExecutorEx.State> graph = AgentExecutorEx.builder()
    .chatModel(chatModel)
    .tools(tools)
    .build(compileConfig);
```

The default serializer for the LangChain4j `AgentExecutorEx` changes from the
standard Java object serializer to its JSON serializer. Persisted checkpoints
must be read with a serializer compatible with the format that created them;
do not mix old binary checkpoint files with the new default.

Spring AI adds reusable sub-agent types:

- `SubAgent` exposes a compiled agent as both a `ToolCallback` and an
  `AgentEx.ToolBehaviour`.
- `CustomSubAgent` wraps an explicitly supplied compiled graph.
- `SkilledReactSubAgent` builds a sub-agent from a `SkillSource`.
- `SkillResource` adapts a Spring `Resource` to `SkillSource`.

`AgentExecutorEx.State.toolExecutionRequests$removeFirst()` is now public, and
`getToolCallByNameFromLastMessage(...)` is available for custom orchestration.
Parallel tool responses are explicitly retained in agent state, and denied
approval responses are routed back through the dispatcher.

The Maven archetype has been updated to the same builder layout and APIs.

## 10. New JSON graph DSL module

The new `langgraph4j-dsl` artifact is included in the BOM:

```xml
<dependency>
  <groupId>org.bsc.langgraph4j</groupId>
  <artifactId>langgraph4j-dsl</artifactId>
</dependency>
```

`JsonDslGenerator` is a `GraphDefinition.Reducer` that exports a graph,
including parallel nodes and nested subgraphs, to a JSON representation:

```java
String json = compiledGraph.reduce(new JsonDslGenerator<>());
```

The module packages `/langgraph4j-dsl.schema.json` for validation. The Studio
web UI consumes the same graph/result model and was updated to React 19 and an
event-driven web-component implementation. These Studio changes do not require
Java application changes unless an application embeds or customizes the web UI.

## 11. Persistence integrations

CockroachDB and DynamoDB savers already exist on the 1.8.19 `develop` baseline,
so they are not new migration requirements for 1.9. Their module versions move
with the rest of the project.

Subgraph release cascading described above applies to savers derived from
`AbstractCheckpointSaver`, including the database-backed implementations.

## 12. Removed Javelit component

`org.bsc.javelit.SpinnerComponent` and its internal spinner implementation were
removed. There is no direct replacement in this branch. Applications using
this optional Java 21 module must replace it with their own progress component
or another Javelit component before upgrading.

## 13. Fixes included in the 1.9 branch

The comparison also contains these behavior fixes:

- State emitted by embedded generators is merged consistently before after-node
  hooks run.
- Subgraph interruption/resume tracks node paths and checkpoint savers more
  reliably, including nested subgraphs.
- `CheckpointSerializer` reads `nodeId` and `nextNodeId` in the correct order.
- Spring AI tool-response messages are serialized and retained in state.
- Spring AI model calls fail clearly when no output is returned.
- LangChain4j and Spring AI streaming correctly handle responses composed only
  of tool-call chunks.
- Collection string formatting tolerates null maps.
- Metadata builders reject null values.
- `FileSystemSaver.list(...)` safely handles a missing target directory.

## 14. Repository and documentation changes

These changes affect project contributors or documentation deployment rather
than library migration:

- Maven/site documentation was reorganized and old generated Spring AI pages
  were removed in favor of maintained module READMEs.
- MkDocs gained Mike-based documentation versioning.
- Studio removed Tailwind/DaisyUI build files and upgraded its React-based UI.
- GitHub Actions and snapshot/site deployment configuration were updated.
- Test classes were renamed and expanded, especially for interruption,
  subgraph, serializer, agent approval, and DSL behavior.
- The root `CHANGELOG.md` differs because `develop` contains the 1.8.x release
  history while the feature branch is preparing the 1.9 line; this guide is the
  migration-focused replacement, not a copy of that release history.
