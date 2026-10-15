# langgraph4j-mongodb-saver

MongoDB-based checkpoint saver for LangGraph4j. It implements the current (V2) saver model: checkpoint history, release tags with versions, error metadata and interruption state.

## Features

- **Official driver**: built on `org.mongodb:mongodb-driver-sync`
- **Three ways to connect**: inject a `MongoClient`, inject a `MongoDatabase`, or give a connection string
- **Safe client lifecycle**: a client or database you supply is never closed by the saver; only a client the saver created from a connection string is closed by `close()`
- **Idempotent setup**: indexes (and the collections they imply) are created on build and can be created any number of times
- **Concurrent writers**: checkpoint order comes from an atomic per-thread sequence, so writers (also from different processes) never overwrite each other
- **Release tags**: every release gets the next `version` for its thread name and can be read back with `tag(...)` / `lastTag(...)`
- **Error and interruption metadata**: kept on the thread document

## Maven Dependency

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-mongodb-saver</artifactId>
    <version>1.9.5</version>
</dependency>
```

## Usage

### Mode 1: Inject a MongoClient (you own it)

```java
MongoClient client = MongoClients.create("mongodb://localhost:27017");

var saver = MongoDBSaver.builder()
        .mongoClient(client)
        .databaseName("langgraph4j")
        .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
        .build();

var graph = new StateGraph<>(AgentState::new)
        .addNode("agent_1", node_async(myAction))
        .addEdge(START, "agent_1")
        .addEdge("agent_1", END);

var workflow = graph.compile(CompileConfig.builder()
        .checkpointSaver(saver)
        .build());
```

`saver.close()` does nothing here: closing `client` is up to you.

### Mode 2: Inject a MongoDatabase (you own it)

```java
var saver = MongoDBSaver.builder()
        .mongoDatabase(client.getDatabase("langgraph4j"))
        .stateSerializer(stateSerializer)
        .build();
```

### Mode 3: Connection string (the saver owns the client)

```java
try (var saver = MongoDBSaver.builder()
        .connectionString("mongodb://localhost:27017/langgraph4j")   // database taken from the URI...
        .databaseName("langgraph4j")                                 // ...or set explicitly
        .stateSerializer(stateSerializer)
        .build()) {
    // use saver
}
```

`MongoDBSaver` is `AutoCloseable`; `close()` is idempotent.

## Builder Options

| Option | Default | Description |
|---|---|---|
| `stateSerializer` | required | Serializer for the state; its content type is stored with each checkpoint |
| `mongoClient` / `mongoDatabase` / `connectionString` | one required | Exactly one must be set |
| `databaseName` | - | Required with `mongoClient`; optional with `connectionString` if the URI has a database; rejected with `mongoDatabase` |
| `collectionPrefix` | `lg4j` | Collections are `<prefix>_thread` and `<prefix>_checkpoint` |
| `createIndexes` | `true` | Create the indexes on build. Set `false` if you manage them yourself |
| `dropCollectionsFirst` | `false` | Drop both collections before creating the indexes. Destructive |

## Data Model

Two collections. Collections are created lazily: creating the indexes (or the first insert) creates them.

### `<prefix>_thread`: one document per execution of a logical thread

| Field | Description |
|---|---|
| `_id` | ObjectId |
| `thread_name` | The `threadId` of the `RunnableConfig` |
| `status` | `ACTIVE` while running, `RELEASED` after a release |
| `version` | Release version (1, 2, ...) per `thread_name`; only on released threads |
| `seq` | Last checkpoint sequence number handed out for this thread |
| `is_interrupted` | `true` after `registerInterruption`, back to `false` when the next checkpoint is added |
| `interruption_reason` | Reason of the pending interruption; kept if the thread is released while interrupted |
| `is_error` | `true` if released with `releaseOnError` |
| `message` | Release message, or the error message for `releaseOnError` |
| `created_at`, `released_at` | Timestamps |

### `<prefix>_checkpoint`: one document per checkpoint

| Field | Description |
|---|---|
| `checkpoint_id` | `Checkpoint.id()` |
| `thread_ref` | `_id` of the owning thread document |
| `seq` | Position in the thread history (newest = highest) |
| `node_id`, `next_node_id` | From the `Checkpoint` |
| `state_data` | State written by the `StateSerializer` |
| `state_content_type` | Content type of the serializer that wrote `state_data` |
| `saved_at` | Timestamp |

### Indexes

| Collection | Index | Purpose |
|---|---|---|
| thread | unique `{thread_name}` where `status = ACTIVE` (`uq_active_thread_name`) | at most one live thread per name; makes creating it race-free |
| thread | unique `{thread_name, version}` where `status = RELEASED` (`uq_released_thread_name_version`) | concurrent releases cannot get the same version |
| checkpoint | unique `{checkpoint_id}` (`uq_checkpoint_id`) | checkpoint identity |
| checkpoint | unique `{thread_ref, seq: -1}` (`uq_thread_ref_seq`) | loads the history newest first |

## Behaviour

- **History**: `list` returns the checkpoints of the live thread, newest first. A `put` with a checkpoint id in the config replaces that checkpoint in place.
- **Release**: `release` marks the live thread `RELEASED` and assigns the next version. The next `put` for the same thread id starts a new live thread with an empty history. Releasing a thread with no live thread is a no-op.
- **Tags**: `tag(config, version)` returns the checkpoints of a released version, `lastTag(config)` the latest one. Both return empty if there is none.
- **Errors**: `releaseOnError` releases like `release` and sets `is_error` and the error message.
- **Interruption**: `registerInterruption` sets `is_interrupted` and `interruption_reason` on the live thread, overwriting any earlier reason. Adding the next checkpoint (the run resumed) clears both. Replacing a checkpoint, as `updateState` does while the run waits, does not.
- **Concurrent releases**: a release acts on the exact run it loaded; if another process released that run first, it reports the version that run got and never releases a newer run of the same thread.
- **Subgraphs**: sub-graph savers are registered and released together with their parent, as for the other savers.

## Limitations

- A MongoDB document is limited to 16 MB, so a single serialized checkpoint state must fit within it.
- No multi-document transactions are used, so a standalone server is enough. If a writer fails between reserving a sequence number and inserting the checkpoint, the sequence has a gap; the history is unaffected.
- A `put` that races with a `release` of the same thread from another process is kept, but it may land in the version being released rather than in the next run, and the `Tag` returned by that release does not list it.
- Not compatible with the other savers' storage: it is a new data model.
