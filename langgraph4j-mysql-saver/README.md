# MySQL Checkpoint Saver

`langgraph4j-mysql-saver` persists LangGraph4j checkpoints in MySQL so graph executions can be resumed, inspected, or released across JVM restarts. It implements the LangGraph4j checkpoint saver contract with a JDBC-backed store and schema initialization helpers.

The module contains two saver implementations:

- [Version 1](./SAVER_V1.md): `MysqlSaver`, the original schema with active/released thread rows and simple release handling.
- [Version 2](./SAVER_V2.md): `MySQLSaverV2`, the newer schema with live thread rows, release tags, error tags, and interruption metadata.

## Features

- Durable checkpoint storage in MySQL.
- Builder-based configuration with an application-managed JDBC `DataSource`.
- Schema creation, reuse, or drop/recreate support through `CreateOption`.
- Serializer-aware payload storage through LangGraph4j `StateSerializer` content types.
- Support for multiple state serializers when reading existing checkpoints with different content types.
- V2 support for released checkpoint versions, error markers, interruption metadata, and read-only dashboard queries.

## Requirements

- Java 17 or later.
- MySQL 8.0 or later.
- MySQL Connector/J on the runtime classpath.
- A LangGraph4j state serializer for the graph state you want to persist.

## Dependency

For Maven:

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-mysql-saver</artifactId>
    <version>1.9.1</version>
</dependency>
```

For Gradle:

```gradle
implementation("org.bsc.langgraph4j:langgraph4j-mysql-saver:1.9.1")
```

## Configuration

Both saver versions share the same builder options:

| Option | Description |
| --- | --- |
| `dataSource(...)` | Supplies the JDBC `DataSource` used for all database operations. |
| `stateSerializer(...)` | Registers a serializer used to encode and decode checkpoint state. At least one serializer is required. Call it again to register additional content types for reading existing checkpoints. |
| `createOption(CREATE_NONE)` | Does not create or modify schema objects. Use when the selected schema already exists. |
| `createOption(CREATE_IF_NOT_EXISTS)` | Creates the selected version's tables when absent. This is the default. |
| `createOption(CREATE_OR_REPLACE)` | Drops the selected version's tables and recreates them. This deletes existing saver data. |

The saver does not create a datasource itself. Configure connection, credentials, TLS, and pool settings on the supplied datasource.

## Quick Start

Use `MySQLSaverV2` for new applications:

```java
import com.mysql.cj.jdbc.MysqlDataSource;
import org.bsc.langgraph4j.checkpoint.CreateOption;
import org.bsc.langgraph4j.checkpoint.MySQLSaverV2;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;

var dataSource = new MysqlDataSource();
dataSource.setURL("jdbc:mysql://localhost:3306/langgraph4j");
dataSource.setUser("app_user");
dataSource.setPassword("secret");

var saver = MySQLSaverV2.builder()
        .dataSource(dataSource)
        .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
        .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
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
Use `V1` when you need compatibility with an existing V1.1 schema or application code based on `MysqlSaver`.

V1 and V2 use different table names and are not schema-compatible. Plan an explicit data migration before switching versions.

See the implementation-specific documentation for details:

- [Version 1](./SAVER_V1.md)
- [Version 2](./SAVER_V2.md)
