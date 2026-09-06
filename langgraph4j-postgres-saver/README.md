# PostgreSQL Checkpoint Saver

`langgraph4j-postgres-saver` persists LangGraph4j checkpoints in PostgreSQL so graph executions can be resumed, inspected, or released across JVM restarts. It implements the LangGraph4j checkpoint saver contract with a JDBC-backed store and schema initialization helpers.

The module contains two saver implementations:

- [Version 1](./SAVER_V1.md): `PostgresSaver`, the original schema with UUID thread rows and simple release handling.
- [Version 2](./SAVER_V2.md): `PostgresSaverV2`, the newer schema with live thread rows, release tags, error tags, and interruption metadata.

## Features

- Durable checkpoint storage in PostgreSQL using JSONB state payloads.
- Builder-based configuration with either direct PostgreSQL connection settings or a supplied `DataSource`.
- Optional schema creation and table drop/recreate support for development and tests.
- Serializer-aware payload storage through LangGraph4j `StateSerializer` content types.
- Support for multiple state serializers when reading existing checkpoints with different content types.
- V2 support for released checkpoint versions, error markers, and interruption metadata.

## Requirements

- Java 17 or later.
- PostgreSQL with JSONB support. PostgreSQL 16.4 or later is recommended for the test and Docker setup used by this module.
- A LangGraph4j state serializer for the graph state you want to persist.

## Dependency

For Maven:

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-postgres-saver</artifactId>
    <version>1.9.0-beta6</version>
</dependency>
```

For Gradle:

```gradle
implementation("org.bsc.langgraph4j:langgraph4j-postgres-saver:1.9.0-beta6")
```

## Configuration

Both saver versions share the same builder options:

| Option | Description |
| --- | --- |
| `stateSerializer(...)` | Registers a serializer used to encode and decode checkpoint state. At least one serializer is required. |
| `host(...)`, `port(...)`, `user(...)`, `password(...)`, `database(...)` | Creates an internal PostgreSQL `PGSimpleDataSource`. |
| `datasource(...)` | Uses an externally managed JDBC `DataSource`. When supplied, direct connection properties are not used. |
| `property(...)`, `properties(...)` | Adds PostgreSQL driver properties to the internally created datasource. |
| `createTables(true)` | Runs the selected version's migration SQL during saver construction. |
| `dropTablesFirst(true)` | Drops existing saver tables before creating them. This also enables `createTables`. |
| `plainTextStateSerializerLegacyMode(true)` | Compatibility mode for legacy `PlainTextStateSerializer` payloads saved as serialized strings. |

## Quick Start

Use `PostgresSaverV2` for new applications:

```java
import org.bsc.langgraph4j.checkpoint.PostgresSaverV2;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;

var saver = PostgresSaverV2.builder()
        .host("localhost")
        .port(5432)
        .user("postgres")
        .password("postgres")
        .database("lg4j-store")
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
Use `V1` when you need compatibility with an existing V1 schema or application code based on `PostgresSaver`.

See the implementation-specific documentation for details:

- [SAVER_V1.md](./SAVER_V1.md)
- [SAVER_V2.md](./SAVER_V2.md)
