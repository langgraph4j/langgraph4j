package org.bsc.langgraph4j;

public interface SnapshotOutput {

    String nextNodeId();

    @Deprecated( forRemoval = true )
    default String next() {
        return nextNodeId();
    }

    String checkpointId();

    default RunnableConfig config( RunnableConfig runnableConfig ) {
        return RunnableConfig.builder(runnableConfig)
                .checkPointId(checkpointId())
                .nextNode(nextNodeId())
                .build();
    }
}
