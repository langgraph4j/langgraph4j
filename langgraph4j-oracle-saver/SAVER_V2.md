# Oracle Checkpoint Saver V2

Version 2 is implemented by `org.bsc.langgraph4j.checkpoint.OracleSaverV2`. It extends the original Oracle saver model with release tags, error metadata, and interruption state.

Use V2 for new applications unless you must keep compatibility with an existing [V1](./SAVER_V1.md) schema.

## Data Architecture

The schema is defined in [`src/main/resources/db/migration/v2.0__init.sql`](./src/main/resources/db/migration/v2.0__init.sql).

```mermaid
erDiagram
    LG4JThread {
        NUMBER thread_id PK
        VARCHAR2 thread_name UK
        NUMBER parent_thread_id FK
        NUMBER is_interrupted
        CLOB message
        TIMESTAMP created_at
    }

    LG4JThreadTag {
        NUMBER thread_id PK
        VARCHAR2 thread_name
        NUMBER released_version
        NUMBER parent_thread_id
        NUMBER is_released
        NUMBER is_error
        CLOB message
        TIMESTAMP created_at
    }

    LG4JCheckpoint {
        VARCHAR2 checkpoint_id PK
        VARCHAR2 parent_checkpoint_id
        NUMBER thread_id
        VARCHAR2 node_id
        VARCHAR2 next_node_id
        CLOB state_data
        VARCHAR2 state_content_type
        TIMESTAMP saved_at
    }

    LG4JThread ||--o{ LG4JCheckpoint : owns_live
    LG4JThreadTag ||--o{ LG4JCheckpoint : owns_released
```

The `LG4JCheckpoint.thread_id` column stores the identity value from `LG4JThread`. The migration intentionally has no foreign key from checkpoints to live threads, so released checkpoints can continue to reference the archived row id after the live thread is deleted.

Indexes `idx_lg4jcheckpoint_thread_id` and `idx_lg4jcheckpoint_thread_id_saved_at_desc` support checkpoint lookup and newest-first history loading.

## Design

V2 keeps only active executions in `LG4JThread`. `thread_id` is an Oracle identity value and `thread_name` is unique.

When a checkpoint is saved, the saver:

1. Merges the live `LG4JThread` row for the logical thread name.
2. Inserts an `LG4JCheckpoint` row that references that identity value.

When the thread is released, the saver:

1. Copies the live thread row into `LG4JThreadTag`.
2. Assigns the next `released_version` for the same `thread_name`.
3. Records release status, error status, optional message, and the original creation time.
4. Deletes the live row from `LG4JThread`.

The checkpoint rows remain associated with the same numeric `thread_id`, which is now represented by `LG4JThreadTag.thread_id`.

## State Serialization

State is serialized through the configured `StateSerializer`. `state_content_type` records the serializer content type so the saver can select the matching registered serializer while loading checkpoints or tags. The encoded payload is Base64 text stored in the `state_data` CLOB.

## Release Tags

`OracleSaverV2.tag(config, version)` loads checkpoints for a released version from `LG4JThreadTag`.

```java
var config = RunnableConfig.builder()
        .threadId("customer-support-thread")
        .build();

var releasedVersion = saver.tag(config, 1);
```

## Interruption Metadata

`registerInterruption(...)` updates the active `LG4JThread` row by setting `is_interrupted = 1` and storing the interruption reason in `message`. This lets external tools or dashboards see that a live thread is waiting for intervention.

## Dashboard Queries

`OracleSaverV2Dashboard` is a read-only helper that does not initialize or mutate the schema. With a suitable Oracle `DataSource`, it exposes `selectAllThreads()` and `selectAllTags()` to retrieve active thread and released-tag records.

## Limitations

- V2 is not schema-compatible with V1. Existing V1 tables require an explicit migration plan before switching classes.
- `LG4JCheckpoint.thread_id` has no active foreign-key constraint in the bundled migration. This is intentional for archived tags, but database cleanup must account for it.
- `thread_name` is unique among live threads, so concurrent executions with the same logical thread name share the same active row.
- Released checkpoints are loaded through `tag(...)`; normal checkpoint loading reads the live thread table.
- Oracle stores boolean-like fields as checked `NUMBER(1)` values, where `0` is false and `1` is true.

## Build a Saver

```java
import oracle.jdbc.datasource.OracleDataSource;
import org.bsc.langgraph4j.checkpoint.CreateOption;
import org.bsc.langgraph4j.checkpoint.OracleSaverV2;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;

var dataSource = new OracleDataSource();
dataSource.setURL("jdbc:oracle:thin:@localhost:1521/FREEPDB1?oracle.jdbc.provider.json=jackson-json-provider");
dataSource.setUser("app_user");
dataSource.setPassword("app_password");

var saver = OracleSaverV2.builder()
        .dataSource(dataSource)
        .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
        .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
        .build();
```

Use the saver when compiling a graph:

```java
var compileConfig = CompileConfig.builder()
        .checkpointSaver(saver)
        .build();

var workflow = graph.compile(compileConfig);
```

## Migration Resource

For managed database migrations, apply:

```text
src/main/resources/db/migration/v2.0__init.sql
```

The saver uses this same resource when `createOption(CREATE_IF_NOT_EXISTS)` or `createOption(CREATE_OR_REPLACE)` is enabled.
