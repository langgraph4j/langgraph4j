# LangGraph4j :: DynamoDB Saver

`DynamoDBSaver` persists LangGraph4j workflow checkpoints in Amazon DynamoDB, with optional
Amazon S3 storage for large state payloads. It implements the `BaseCheckpointSaver` contract
through `AbstractCheckpointSaver` and supports checkpoint history, updates, and thread release.

## Maven Dependency

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-dynamodb-saver</artifactId>
    <version>${langgraph4j.version}</version>
</dependency>
```

The module includes the AWS SDK v2 DynamoDB client and URL Connection HTTP client.
The S3 dependency is optional; add it explicitly when using S3 offloading (see below).

## Usage

Configure AWS credentials in your environment and choose a region for the injected client.
The following Java statements can be placed inside an application method that declares
`throws Exception`.

```java
// Keep the client open for the lifetime of all workflows using this saver.
try (var client = DynamoDbClient.builder()
        .region(Region.US_EAST_1)
        .build()) {
    var saver = DynamoDBSaver.builder()
            .tableName("langgraph4j-checkpoints")
            .dynamoDbClient(client)
            .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
            .createTableIfNotExists(true)
            .build();

    // Return a state update from the node; the saver persists workflow checkpoints.
    var graph = new StateGraph<>(AgentState::new)
            .addNode("greet", node_async(state -> Map.of("greeting", "Hello!")))
            .addEdge(StateGraph.START, "greet")
            .addEdge("greet", StateGraph.END);

    // Retain checkpoints after completion so this thread's state remains readable.
    var workflow = graph.compile(CompileConfig.builder()
            .checkpointSaver(saver)
            .releaseThread(false)
            .build());
    var config = RunnableConfig.builder().threadId("conversation-1").build();

    // Reuse the thread ID to access the persisted state and checkpoint history.
    workflow.invoke(GraphInput.args(Map.of("input", "Hi")), config);
    var latest = workflow.lastStateOf(config).orElseThrow();
    var history = workflow.getStateHistory(config);
}
```

The caller owns the DynamoDB and S3 clients and must close them when no longer needed.
`DynamoDBSaver` does not implement `AutoCloseable`. Use a compatible state serializer when
reading existing checkpoints; the saver checks the stored content type during deserialization.

## Builder Options

| Option | Default | Description |
| --- | --- | --- |
| `tableName(String)` | Required | DynamoDB table name. |
| `dynamoDbClient(DynamoDbClient)` | Required | Client configured with credentials, region, and any endpoint override. |
| `stateSerializer(StateSerializer)` | Required | Serializer for the agent state. |
| `createTableIfNotExists(boolean)` | `false` | Create the table during `build()` if absent, using `PK` and `SK` string keys and `PAY_PER_REQUEST` billing. |
| `dropTableFirst(boolean)` | `false` | Delete and recreate the entire table during `build()`. Implies table creation; intended for disposable test data. |
| `ttlSeconds(long)` | Unset | Retention duration in seconds. Use a positive value; see TTL setup below. |
| `s3Bucket(String)` | Unset | Existing bucket for serialized payloads larger than 350 KiB. |
| `s3Client(S3Client)` | Unset | Required when `s3Bucket` is set. The caller configures and owns this client. |
| `s3KeyPrefix(String)` | Unset | Optional prefix for S3 object keys, such as `my-app/dev`. |

For an externally provisioned table, define `PK` as the string partition key and `SK` as
the string sort key. No secondary index is required. Leave `createTableIfNotExists` disabled
when table provisioning is managed outside the application.

## Local Development

With DynamoDB Local running on port 8000, replace the client in the usage example with:

```java
// Dummy credentials are for the local endpoint only; close this client after use.
var client = DynamoDbClient.builder()
        .endpointOverride(URI.create("http://localhost:8000"))
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("dummy", "dummy")))
        .build();
```

Enable `createTableIfNotExists(true)` on the saver for initial setup.
`dropTableFirst(true)` deletes all data in the configured table, including other threads.

## Large Payloads with S3

Add the S3 SDK dependency. This checkout uses AWS SDK `2.27.21`; keep the S3 version aligned
with the SDK version used by the saver.

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.27.21</version>
</dependency>
```

Configure an existing bucket and inject an S3 client alongside your DynamoDB client:

```java
import software.amazon.awssdk.services.s3.S3Client;

// Keep both clients open while workflows use the saver; client is the DynamoDB client above.
try (var s3 = S3Client.builder().region(Region.US_EAST_1).build()) {
    var s3Saver = DynamoDBSaver.builder()
            .tableName("langgraph4j-checkpoints")
            .dynamoDbClient(client)
            .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
            .s3Bucket("my-checkpoint-bucket")
            .s3KeyPrefix("my-app/dev")
            .s3Client(s3)
            .build();

    // Compile and run workflows with s3Saver before closing the S3 client.
}
```

Routing uses the size of the UTF-8 bytes produced by `StateSerializer.writeDataAsString`,
including any serialization encoding overhead. Payloads **larger than 350 KiB** go to S3
when configured; smaller payloads remain in DynamoDB. Without S3, all payloads stay in
DynamoDB and must fit its item-size limit; the saver does not split oversized payloads.

S3 objects use `<prefix>/<threadId>/checkpoints/<checkpointId>` (without the prefix segment
when unset). DynamoDB metadata records the storage location and reference key so reads
retrieve the payload from the appropriate backend. The saver does not create the bucket.

## TTL and Cleanup

Set `ttlSeconds(...)` to a positive duration to write a numeric `ttl` attribute containing
the expiration time in epoch seconds on DynamoDB metadata and chunks. Release markers hold the
thread generation and never expire.
**Enable DynamoDB TTL on the table's `ttl` attribute separately**; the saver does not enable it.
Reads do not filter expired timestamps, so expiration is not an application-level read cutoff.

When S3 and a positive TTL are configured, building the saver also attempts to add a bucket
lifecycle rule. It rounds the duration up to whole days, with a minimum of one day, and
tags payload objects with `ttl-days`. The rule filters by that tag and the configured
prefix when present. Lifecycle configuration failures are logged. Ensure the client has
the required bucket lifecycle and object tagging permissions if you use this feature.

Thread release and deletion have different effects:

- `saver.release(config)` archives the current execution and returns a `Tag` containing its
  checkpoints. It starts a new *generation* of the thread: the released metadata and payloads
  are retained under their generation, and the same thread id can be reused for a fresh
  execution that starts empty.
- `saver.deleteThread("conversation-1")` requests permanent deletion of the thread's metadata
  of every generation, referenced DynamoDB or S3 payloads, and release marker. Calling it for
  a missing thread is safe. Batch cleanup failures can be logged, so inspect logs when
  verifying removal.

Use `releaseThread(false)` when compiling workflows whose history must remain available
after completion, as in the usage example.

## Storage Model and Current Limits

All DynamoDB items share one table:

| Item | `PK` | `SK` |
| --- | --- | --- |
| Checkpoint metadata (generation 0) | `CHECKPOINT_<threadId>` | `<checkpointId>` |
| Checkpoint metadata (generation N > 0) | `CHECKPOINT_<threadId>#<N>` | `<checkpointId>` |
| Serialized state stored in DynamoDB | `CHUNK_<threadId>#<checkpointId>` | `CHUNK` |
| Released thread marker (current generation) | `RELEASED_<threadId>` | `MARKER` |

The current generation of a thread is the `generation` attribute of its release marker. A
thread that was never released has no marker and uses generation 0, the original key format.
A marker written by earlier versions has no `generation` attribute and is read as
generation 1, so checkpoints released before upgrading stay hidden without a migration.
Avoid releasing the same thread from older and newer saver versions at the same time during
a rolling upgrade: older versions overwrite the marker without the `generation` attribute.

Metadata includes node IDs, serializer content type, `savedAt`, `ref_loc` (`DYNAMODB` or `S3`),
and `ref_key`. History queries are paginated and sorted by `savedAt` descending in memory;
loading history retrieves each checkpoint payload.

- Payload and metadata writes are separate operations, without a transaction. Checkpoint reads
  use the SDK's default consistency; only the release marker is read with strong consistency.
- Insert paths use conditional writes to skip existing checkpoint IDs. Updates delete
  the old checkpoint metadata and DynamoDB chunk before writing the replacement.
- Missing payloads and checkpoints that fail deserialization are logged and skipped during
  loading. Use a matching serializer and inspect logs if history appears incomplete.
- `tag(config, version)` returns empty; persisted release-tag lookup is not implemented.
  `registerInterruption(...)` returns the supplied metadata without persisting it.
- Parent checkpoint metadata depends on the checkpoint ID supplied by the core framework;
  the lineage integration test is currently disabled pending core support.
- Replacing S3-backed checkpoints does not delete their previous S3 objects. Use lifecycle
  rules for residual objects; thread deletion discovers payloads through remaining metadata.

## Tests

The tests use Testcontainers with DynamoDB Local and MinIO. Docker must be running, and
host ports 8344 and 8345 must be available for the saver and storage-strategy tests.
The module skips tests by default; explicitly enable them when running from the repository root:

```sh
# Build the module and its reactor dependencies, including the core test JAR.
./mvnw -pl langgraph4j-dynamodb-saver -am test -DskipTests=false
```
