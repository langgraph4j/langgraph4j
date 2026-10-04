package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression test: loading checkpoints must return its connection. */
public class SQLiteSaverConnectionLeakTest {

    static SQLiteDataSource dataSource() {
        var ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:".concat(Path.of("target").resolve("SQLiteSaverConnectionLeakTest.db").toString()));
        return ds;
    }

    @Test
    void everyConnectionTheSaverBorrowsIsClosed() throws Exception {
        var ds = new TrackingDataSource(dataSource());
        BaseCheckpointSaver saver = SQLiteSaverV2.builder()
                .datasource(ds)
                .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
                .createTables(true)
                .build();
        assertEquals(0, ds.openConnections(), "after build");

        var config = RunnableConfig.builder().threadId("connection-leak-test").build();

        saver.list(config);
        assertEquals(0, ds.openConnections(), "after list");

        saver.get(config);
        assertEquals(0, ds.openConnections(), "after get");

        var checkpoint = Checkpoint.builder()
                .state(Map.of("step", 1))
                .nodeId("node")
                .nextNodeId("next")
                .build();
        config = saver.put(config, checkpoint);
        assertEquals(0, ds.openConnections(), "after put");

        assertTrue(saver.get(config).isPresent());
        assertEquals(0, ds.openConnections(), "after get of a stored checkpoint");

        saver.release(config);
        assertEquals(0, ds.openConnections(), "after release");
    }
}
