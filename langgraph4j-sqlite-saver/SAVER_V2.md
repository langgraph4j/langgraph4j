# SQLite Checkpoint Saver V2

Version 2 is implemented by `org.bsc.langgraph4j.checkpoint.SQLiteSaverV2`. It extends the original SQLite saver model with release tags, error metadata, and interruption state.

Use V2 for new applications unless you must keep compatibility with an existing [V1](./SAVER_V1.md) schema.

## Data Architecture

The schema is defined in [`src/main/resources/db/migration/v2.0__init.sql`](./src/main/resources/db/migration/v2.0__init.sql).

```mermaid
erDiagram
    LG4JThread {
        INTEGER thread_id PK
        TEXT thread_name UK
        INTEGER parent_thread_id FK
        INTEGER is_interrupted
        TEXT message
        TEXT created_at
    }

    LG4JThreadTag {
        INTEGER thread_id PK
        TEXT thread_name
        INTEGER released_version
        INTEGER parent_thread_id
        INTEGER is_released
        INTEGER is_error
        TEXT message
        TEXT created_at
    }

    LG4JCheckpoint {
        TEXT checkpoint_id UK
        TEXT parent_checkpoint_id
        INTEGER thread_id
        TEXT node_id
        TEXT next_node_id
        TEXT state_data
        TEXT state_content_type
        TEXT saved_at
    }

    LG4JThread ||--o{ LG4JCheckpoint : owns_live
    LG4JThreadTag ||--o{ LG4JCheckpoint : owns_released
```

The `LG4JCheckpoint.thread_id` column stores the row id that came from `LG4JThread`. The migration does not define a foreign key from checkpoints to live threads so released checkpoints can continue to reference the archived row id after the live thread is deleted.

Indexes:

- `idx_lg4jcheckpoint_thread_id` supports lookup by thread row.
- `idx_lg4jcheckpoint_thread_id_saved_at_desc` supports loading checkpoint histories newest first.

## Design

V2 keeps only active executions in `LG4JThread`. The row id is an SQLite autoincrement integer, and `thread_name` is unique.

When a checkpoint is saved, the saver:

1. Inserts or refreshes the live `LG4JThread` row for the logical thread name.
2. Reads the `thread_id` with SQLite `RETURNING`.
3. Inserts a `LG4JCheckpoint` row that references that identity value.

When the thread is released, the saver:

1. Copies the live thread row into `LG4JThreadTag`.
2. Assigns the next `released_version` for the same `thread_name`.
3. Records release status, error status, optional message, and original creation time.
4. Deletes the live row from `LG4JThread`.

The checkpoint rows remain associated with the same numeric `thread_id`, which is now represented by `LG4JThreadTag.thread_id`.

## State Serialization

It serializes state through the configured `StateSerializer` and `state_content_type` records the serializer content type so the saver can select the correct registered serializer while loading checkpoints or tags.

## Release Tags

`SQLiteSaverV2.tag(config, version)` loads checkpoints for a released version from `LG4JThreadTag`.

```java
var config = RunnableConfig.builder()
        .threadId("customer-support-thread")
        .build();

var releasedVersion = saver.tag(config, 1);
```

## Interruption Metadata

`registerInterruption(...)` updates the active `LG4JThread` row by setting `is_interrupted = 1` and storing the interruption reason in `message`. This lets external tools or dashboards see that a live thread is waiting for intervention.

## Limitations

- V2 is not schema-compatible with V1. Existing V1 tables require an explicit migration plan before switching classes.
- `LG4JCheckpoint.thread_id` has no active foreign-key constraint in the bundled migration. This is intentional for archived tags, but database cleanup must account for it.
- `thread_name` is unique among live threads, so concurrent executions with the same logical thread name share the same active row.
- Released checkpoints are loaded through `tag(...)`; normal checkpoint loading reads the live thread table.
- Boolean values are stored as checked integer values, where `0` is false and `1` is true.
- `checkpoint_id` is unique but not declared as the table primary key in the bundled schema.

## Build a Saver

Using a database path:

```java
import org.bsc.langgraph4j.checkpoint.SQLiteSaverV2;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;

var saver = SQLiteSaverV2.builder()
        .databasePath("target/lg4j-store.db")
        .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
        .createTables(true)
        .build();
```

Using a read-only dashboard-style configuration:

```java
import org.bsc.langgraph4j.checkpoint.SQLiteSaverV2Dashboard;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.sqlite.SQLiteConfig;

var sqliteConfig = new SQLiteConfig();
sqliteConfig.setReadOnly(true);

var dashboardSaver = SQLiteSaverV2Dashboard.builder()
        .databasePath("target/lg4j-store.db")
        .config(sqliteConfig)
        .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
        .build();
```

Using an existing `DataSource`:

```java
import org.bsc.langgraph4j.checkpoint.SQLiteSaverV2;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.sqlite.SQLiteDataSource;

var dataSource = new SQLiteDataSource();
dataSource.setUrl("jdbc:sqlite:target/lg4j-store.db");

var saver = SQLiteSaverV2.builder()
        .datasource(dataSource)
        .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
        .createTables(true)
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

The saver uses this same resource when `createTables(true)` is enabled.
