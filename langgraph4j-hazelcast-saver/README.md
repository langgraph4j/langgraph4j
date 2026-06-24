# LangGraph4j :: Hazelcast Saver

A [`BaseCheckpointSaver`](../langgraph4j-core/src/main/java/org/bsc/langgraph4j/checkpoint/BaseCheckpointSaver.java)
implementation that persists LangGraph4j workflow checkpoints in a Hazelcast distributed map, so
workflow state survives process restarts and can be shared across the members of a Hazelcast cluster.

## Storage model

All checkpoints of a single thread are stored as **one map entry**: the key is the `threadId` and
the value is the serialized, time-ordered list of that thread's checkpoints (most recent first).
This is the same whole-list-per-key model that the core `FileSystemSaver` uses (one file per
thread); here a map entry takes the place of the file.

Serialization reuses the framework's checkpoint-list serializers, chosen from the required
`StateSerializer`: a `JacksonStateSerializer` selects `JacksonCheckpointListSerializer` (JSON,
stored as the map value directly), and any other `StateSerializer` selects `CheckpointListSerializer`
(binary, stored Base64-encoded). Read the list back with a saver configured the same way it was
written.

This single-value-per-thread layout keeps reads and writes atomic per thread and is the natural fit
for a `CPMap`, which exposes only get-by-key with no key enumeration. It is chosen for simplicity and
strong per-thread consistency.

> **Caveat — write amplification.** Because a thread's checkpoints live in a single value, each
> appended checkpoint re-serializes and rewrites the **entire** list for that thread (an `O(n)` write
> for the `n`-th checkpoint). For typical workflows (tens of checkpoints, modest state) this is
> negligible. For long-lived threads with large state, prefer pruning history (release threads you no
> longer need) and be mindful of `CPMap` per-map size limits.

## Backing structure: IMap (CE) vs. CPMap (Enterprise)

The map type is selected with `mapType(MapType)`:

| Map type | value | Consistency | Edition | Jar |
| --- | --- | --- | --- | --- |
| `IMap` (default) | `MapType.I_MAP` | AP, backup-replicated; survives one member loss, not linearizable | Community | `com.hazelcast:hazelcast` |
| `CPMap` | `MapType.CP_MAP` | Linearizable, Raft-backed (CP Subsystem); an acknowledged checkpoint is never lost while a CP majority is available | Enterprise | `com.hazelcast:hazelcast-enterprise` |

For checkpointing you usually want the stronger `CPMap` guarantee, but it requires Hazelcast
Enterprise (license + an enabled CP Subsystem, which needs a 3-member CP group). `IMap` runs on the
free Community Edition.

## Dependency

Hazelcast is a **`provided`** dependency: you supply the jar, so the same saver works on either
edition. The bundled Java client is part of the Hazelcast jar — no extra dependency is needed for
the client/server topology.

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-hazelcast-saver</artifactId>
    <version>${langgraph4j.version}</version>
</dependency>

<!-- Community Edition (IMap), or swap for com.hazelcast:hazelcast-enterprise to use CPMap -->
<dependency>
    <groupId>com.hazelcast</groupId>
    <artifactId>hazelcast</artifactId>
    <version>5.7.0</version>
</dependency>
```

## Usage

The saver is topology-agnostic: it accepts any `HazelcastInstance`, whether an embedded member or a
thin client connected to a remote cluster. The caller owns the instance's lifecycle.

### Embedded member (Hazelcast runs in-process), IMap / CE

```java
HazelcastInstance hz = Hazelcast.newHazelcastInstance(new Config());

var saver = HazelcastSaver.builder()
        .hazelcastInstance(hz)
        .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
        .build();
```

### Client to a remote cluster, CPMap / Enterprise

```java
ClientConfig cfg = new ClientConfig();
cfg.getNetworkConfig().addAddress("hazelcast-host:5701");
HazelcastInstance hz = HazelcastClient.newHazelcastClient(cfg);

var saver = HazelcastSaver.builder()
        .hazelcastInstance(hz)
        .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
        .mapType(HazelcastSaver.MapType.CP_MAP)   // linearizable CP map (Enterprise)
        .mapName("agentCheckpoints")
        .build();
```

### State serialization (JSON vs. binary)

A `StateSerializer` is **required**. A `JacksonStateSerializer` stores checkpoints as JSON; any other
`StateSerializer` (e.g. `ObjectStreamStateSerializer`) stores them as Base64-encoded binary. Reload
checkpoints with a saver configured the same way they were written.

```java
// Binary (Java serialization)
.stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))

// JSON (subclass JacksonStateSerializer for your state type)
.stateSerializer(myJacksonStateSerializer)
```

## Builder options

| Option | Default | Description |
| --- | --- | --- |
| `hazelcastInstance(HazelcastInstance)` | — (required) | Embedded member or client instance |
| `mapName(String)` | `langgraph4j-checkpoints` | Name of the backing Hazelcast map |
| `mapType(MapType)` | `MapType.I_MAP` | `MapType.CP_MAP` selects the Enterprise CPMap backing |
| `stateSerializer(StateSerializer)` | — (required) | JSON if a `JacksonStateSerializer`, otherwise Base64 binary |

## Tests

- `HazelcastSaverTest` — runs against an embedded Community-Edition member (no Docker, no license)
  and is executed in CI.
- `HazelcastCPMapSaverITest` — exercises the Enterprise CPMap backing. It is excluded from CI
  (`*ITest`) and skips unless a license is provided via the `HZ_LICENSEKEY` environment variable or
  the `hazelcast.enterprise.license.key` system property.

### Running the CPMap (Enterprise) integration test

The `enterprise-it` Maven profile swaps the Community Edition jar for `com.hazelcast:hazelcast-enterprise`
(a drop-in superset — the two editions must never coexist on the classpath), adds the Hazelcast
Enterprise Maven repository, and runs the otherwise-excluded `*ITest`:

```bash
HZ_LICENSEKEY='<your enterprise license>' \
  ./mvnw -pl langgraph4j-hazelcast-saver -Penterprise-it test
```

The profile is opt-in: normal builds (and `-Prelease`) keep the default `community` profile and the
CE jar, so CI is unaffected.
