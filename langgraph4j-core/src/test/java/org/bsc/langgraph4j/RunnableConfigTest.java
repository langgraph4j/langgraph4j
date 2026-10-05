package org.bsc.langgraph4j;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RunnableConfigTest {

    @Test
    public void runnableConfigUpdateMetadataTest() {
        var config = RunnableConfig.builder()
                        .addMetadata( "nodeId", "test1")
                        .addMetadata( "graphPath", "test1/test2")
                        .build();

        assertTrue( config.metadata("nodeId").isPresent() );
        assertEquals( "test1", config.metadata("nodeId").get() );
        assertTrue( config.metadata("graphPath").isPresent() );
        assertEquals( "test1/test2", config.metadata("graphPath").get() );

        config = config.updateMetadata( Map.of( "nodeId", "test2" ) );

        assertTrue( config.metadata("nodeId").isPresent() );
        assertEquals( "test2", config.metadata("nodeId").get() );
        assertTrue( config.metadata("graphPath").isPresent() );
        assertEquals( "test1/test2", config.metadata("graphPath").get() );
    }

    @Test
    public void recursionLimitIsPreservedByConfigCopies() {
        var config = RunnableConfig.builder()
                .recursionLimit(10)
                .build();

        assertEquals(10, config.recursionLimit().orElseThrow());
        assertEquals(10, RunnableConfig.builder(config).build().recursionLimit().orElseThrow());
        assertEquals(10, config.withStreamMode(CompiledGraph.StreamMode.SNAPSHOTS).recursionLimit().orElseThrow());
        assertEquals(10, config.withCheckPointId("checkpoint-1").recursionLimit().orElseThrow());
        assertEquals(10, config.updateMetadata(Map.of("key", "value")).recursionLimit().orElseThrow());
    }

    @Test
    public void recursionLimitMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> RunnableConfig.builder().recursionLimit(0));
        assertThrows(IllegalArgumentException.class,
                () -> RunnableConfig.builder().recursionLimit(-1));
    }

    @Test
    public void streamExecutorIsPreservedByConfigCopies() {
        Executor executor = Runnable::run;
        var config = RunnableConfig.builder()
                .emitterExecutor(executor)
                .build();

        assertSame(executor, config.emitterExecutor().orElseThrow());
        assertSame(executor, RunnableConfig.builder(config).build().emitterExecutor().orElseThrow());
        assertSame(executor, config.withStreamMode(CompiledGraph.StreamMode.SNAPSHOTS).emitterExecutor().orElseThrow());
        assertSame(executor, config.withCheckPointId("checkpoint-1").emitterExecutor().orElseThrow());
        assertSame(executor, config.updateMetadata(Map.of("key", "value")).emitterExecutor().orElseThrow());
        assertSame(executor, config.updateMetadata(Map.of("key", "value")).removeMetadata("key").emitterExecutor().orElseThrow());
        assertTrue(RunnableConfig.empty().emitterExecutor().isEmpty());
    }

    @Test
    public void streamExecutorMustNotBeNull() {
        assertThrows(NullPointerException.class,
                () -> RunnableConfig.builder().emitterExecutor(null));
    }
}
