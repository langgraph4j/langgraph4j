# SQLite Checkpoint Saver

`langgraph4j-sqlite-saver` persists LangGraph4j checkpoints in a local SQLite database so graph executions can be resumed, inspected, or released across JVM restarts. It implements the LangGraph4j checkpoint saver contract with a JDBC-backed embedded database store and schema initialization helpers.

The module contains two saver implementations:

- [Version 1](./SAVER_V1.md): `SQLiteSaver`, the original schema with active/released thread rows and simple release handling.
- [Version 2](./SAVER_V2.md): `SQLiteSaverV2`, the newer schema with live thread rows, release tags, error tags, and interruption metadata.

## Features

- Durable checkpoint storage in a local SQLite file.
- Builder-based configuration with a database path, JDBC URL, or supplied `DataSource`.
- Optional schema creation and table drop/recreate support for development and tests.
- Serializer-aware payload storage through LangGraph4j `StateSerializer` content types.
- Support for multiple state serializers when reading existing checkpoints with different content types.
- V2 support for released checkpoint versions, error markers, and interruption metadata.

## Requirements

- Java 17 or later.
- SQLite through the bundled `org.xerial:sqlite-jdbc` dependency.
- A LangGraph4j state serializer for the graph state you want to persist.

## Dependency

For Maven:

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-sqlite-saver</artifactId>
    <version>1.9-beta5</version>
</dependency>
```

For Gradle:

```gradle
implementation("org.bsc.langgraph4j:langgraph4j-sqlite-saver:1.9-beta5")
```

## Configuration

Both saver versions share the same builder options:

| Option | Description |
| --- | --- |
| `stateSerializer(...)` | Registers a serializer used to encode and decode checkpoint state. At least one serializer is required. |
| `databasePath(...)` | Creates an internal SQLite datasource using `jdbc:sqlite:<databasePath>`. |
| `url(...)` | Creates an internal SQLite datasource from a full JDBC URL. |
| `config(...)` | Supplies an `SQLiteConfig` for the internally created datasource. |
| `datasource(...)` | Uses an externally managed JDBC `DataSource`. When supplied, `databasePath`, `url`, and `config` are not used. |
| `createTables(true)` | Runs the selected version's migration SQL during saver construction. |
| `dropTablesFirst(true)` | Drops existing saver tables before creating them. This also enables `createTables`. |
| `plainTextStateSerializerLegacyMode(true)` | Compatibility mode for legacy `PlainTextStateSerializer` payloads saved as serialized strings. |

The saver enables SQLite foreign-key enforcement for its own connections with `PRAGMA foreign_keys = ON`.

## Quick Start

Use `SQLiteSaverV2` for new applications:

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

Then pass the saver to graph compilation:

```java
var compileConfig = CompileConfig.builder()
        .checkpointSaver(saver)
        .build();

var workflow = graph.compile(compileConfig);
```

## Choosing a Version

Use `V2` for new work. It preserves released runs in `LG4JThreadTag`, supports versioned tag lookup, and records interruptions and release errors.
Use `V1` when you need compatibility with an existing V1 schema or application code based on `SQLiteSaver`.

See the implementation-specific documentation for details:

- [SAVER_V1.md](./SAVER_V1.md)
- [SAVER_V2.md](./SAVER_V2.md)
