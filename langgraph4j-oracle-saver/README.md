# Oracle Checkpoint Saver

`langgraph4j-oracle-saver` persists LangGraph4j checkpoints in Oracle Database so graph executions can be resumed, inspected, or released across JVM restarts. It implements the LangGraph4j checkpoint saver contract with JDBC-backed Oracle storage and schema initialization helpers.

The module contains two saver implementations:

- [Version 1](./SAVER_V1.md): `OracleSaver`, the original schema with active/released thread rows and simple release handling.
- [Version 2](./SAVER_V2.md): `OracleSaverV2`, the newer schema with live thread rows, release tags, error tags, and interruption metadata.

## Features

- Durable checkpoint storage in Oracle Database.
- Builder-based configuration with an application-managed JDBC `DataSource`.
- Optional schema creation or drop/recreate support for development and tests.
- Serializer-aware payload storage through LangGraph4j `StateSerializer` content types.
- Support for multiple state serializers when reading existing checkpoints with different content types.
- V2 support for released checkpoint versions, error markers, interruption metadata, and dashboard queries.

## Requirements

- Java 17 or later.
- Oracle Database 23ai or a compatible Oracle version.
- Oracle JDBC 11 driver and the Jackson OSON provider; both are transitive module dependencies.
- A LangGraph4j state serializer for the graph state you want to persist.

## Dependency

For Maven:

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-oracle-saver</artifactId>
    <version>1.9-beta5</version>
</dependency>
```

For Gradle:

```gradle
implementation("org.bsc.langgraph4j:langgraph4j-oracle-saver:1.9-beta5")
```

## Configuration

Both saver versions share the same builder options:

| Option | Description |
| --- | --- |
| `dataSource(...)` | Supplies the JDBC `DataSource` used for all Oracle connections. |
| `stateSerializer(...)` | Registers a serializer used to encode and decode checkpoint state. Register at least one serializer before saving state. |
| `createOption(CREATE_NONE)` | Reuses an existing schema without issuing DDL. |
| `createOption(CREATE_IF_NOT_EXISTS)` | Creates the selected version's tables when they do not exist. This is the default. |
| `createOption(CREATE_OR_REPLACE)` | Drops the selected version's saver tables and recreates them. This removes existing saver data. |

The Oracle JDBC URL must enable the Jackson JSON provider, for example by appending `?oracle.jdbc.provider.json=jackson-json-provider`.

## Quick Start

Use `OracleSaverV2` for new applications:

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

Then pass the saver to graph compilation:

```java
var compileConfig = CompileConfig.builder()
        .checkpointSaver(saver)
        .build();

var workflow = graph.compile(compileConfig);
```

## Choosing a Version

Use `V2` for new work. It preserves released runs in `LG4JThreadTag`, supports versioned tag lookup, and records interruptions and release errors.
Use `V1` when you need compatibility with an existing V1 schema or application code based on `OracleSaver`.

See the implementation-specific documentation for details:

- [Version 1](./SAVER_V1.md)
- [Version 2](./SAVER_V2.md)
