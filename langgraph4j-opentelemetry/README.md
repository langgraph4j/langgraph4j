# LangGraph4j OpenTelemetry

[OpenTelemetry] instrumentation for LangGraph4j workflows. This module provides hooks that emit spans, events, and logs for nodes and edges, plus helpers to attach attributes from workflow state.

## Overview

This module is currently focused on provide  patterns and utilities to integrate [OpenTelemetry] with LangGraph4j:

- Hook implementations to wrap node/edge execution with spans.
- Attribute helpers to serialize state and config into [OpenTelemetry] attributes.
- A simple internal OTLP/HTTP collector for local testing.
- Docker compose setups for [Jaeger](https://www.jaegertracing.io) or Grafana/Loki + [OpenTelemetry] Collector.

## Requirements

- Java 17+
- OpenTelemetry SDK (provided by this module)
- A configured OTLP exporter (Jaeger, Collector, etc.)

## Install

Add the dependency:

```xml
<dependency>
  <groupId>org.bsc.langgraph4j</groupId>
  <artifactId>langgraph4j-opentelemetry</artifactId>
  <version>1.8.12</version>
</dependency>
```

## LangGraph4j OpenTelemetry Hooks

This module provides to built-in hooks, either for node and edge, to manage [OpenTelemetry] tracing using span and event.

- `OTELWrapCallTraceHook` creates spans for node and edge evaluation.
  - Span attributes include `RunnableConfig` fields and serialized state.
  - Span events are emitted at start/end with state or command details.
- `OTELWrapCallTraceSetParentHook` creates a parent span to group node/edge calls within its scope.

## Example

In the test part of project is available a integration test `OTELObservationLangraph4jITest` that provides a basic sample of usage of the Hooks above-mentioned.
Below a briefly explanation of the main parts:

### Configure OpenTelemetry

This module uses `AutoConfiguredOpenTelemetrySdk` and standard [OpenTelemetry] system properties (see `otel-config.properties`).
For example, set `otel.exporter.otlp.endpoint` and enable exporters in your runtime config or properties file.

```java
var autoConfig = AutoConfiguredOpenTelemetrySdk.initialize();
var otel = autoConfig.getOpenTelemetrySdk();
OpenTelemetryAppender.install(otel);
```

### Attach OpenTelemetry hooks to a workflow

```java
var stateSerializer = new ObjectStreamStateSerializer<>(MyState::new);

var otelHook = new OTELWrapCallTraceHook<MyState>(stateSerializer);

var parentHook = OTELWrapCallTraceSetParentHook.<MyState>builder()
        .scope( "MyWorkflow" )
        .groupName( "stream" )
        .build();

var workflow = new StateGraph<>(MyState.SCHEMA, stateSerializer)
    .addWrapCallNodeHook(otelHook)
    .addWrapCallEdgeHook(otelHook)
    // IMPORTANT !!! REMEMBER TO ADD THIS AFTER CHILDREN HOOKS BECAUSE WRAP HOOKS ARE ADDED USING LIFO STRATEGY    
    .addWrapCallNodeHook(parentHook) 
    .addWrapCallEdgeHook(parentHook) 
    // add nodes/edges...
    .compile();
```


## Local collectors (Docker)

The module includes Docker compose files for common setups:

### Jaeger all-in-one (Trace collector)

```bash
docker compose -f src/docker/docker-compose-jaeger.yml up
```

Then open the Jaeger UI on port `16686`.

### Grafana + Loki + OTel Collector (Log collector)

```bash
docker compose -f src/docker/docker-compose.yml up
```

This starts Grafana on `3000`, Loki on `3100`, and the collector on `4317/4318`.

## Internal HTTP collector (tests/dev)

`OTELInternalHttpCollector` is a small OTLP/HTTP receiver for local testing.
It writes logs, traces, and metrics to JSON files in a target directory.

```java
var collector = OTELInternalHttpCollector.builder()
    .outputDir(Path.of("build/otel"))
    .port(4318)
    .buildAndStart();
```

## Tests

This module includes integration-style tests that initialize the [OpenTelemetry] SDK using
`src/test/resources/otel-config.properties`. The `*ITest` tests are excluded
from default Surefire runs.



[OpenTelemetry]: https://opentelemetry.io/docs/what-is-opentelemetry/
