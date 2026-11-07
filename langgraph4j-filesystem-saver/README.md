# File system checkpoint saver

`langgraph4j-filesystem-saver` provides `FileSystemSaverV2`, which stores active
checkpoint lists in a directory and moves released threads into numbered files
under its `released` subdirectory. The directory is created when the saver is
constructed. Use a separate directory for each application or environment.

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-filesystem-saver</artifactId>
    <version>1.9.9</version>
</dependency>
```

```java
var saver = new FileSystemSaverV2(
        Path.of("checkpoints"),
        new JacksonStateSerializer<>(AgentState::new));

var workflow = graph.compile(CompileConfig.builder()
        .checkpointSaver(saver)
        .build());
```

The saver accepts either a state serializer or a checkpoint-list serializer.
It writes a JSON file for each thread, even when the state serializer uses
binary data. Reopen it with the same serializer to read existing checkpoints.
`release(config)` archives the active file; `tag(config, version)` reads an
archived version.

> **Note:** version 1 of `FileSystemSaver`
> (`org.bsc.langgraph4j.checkpoint.FileSystemSaver`) has been left in the
> `langgraph4j-core` module to keep backward compatibility.
