package org.bsc.langgraph4j.checkpoint.agui;

import org.bsc.langgraph4j.agui.sdk.AGUIAgentBase;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.SQLiteSaverV2;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;

public abstract class AGUIAbstractAgent extends AGUIAgentBase  {
    /**
     * Creates a new agent base with the given identifier.
     *
     * @param id the unique identifier of this agent; must not be {@code null}
     */
    public AGUIAbstractAgent(String id) {
        super(id);
    }

    protected BaseCheckpointSaver buildSaver(StateSerializer<?> stateSerializer) throws Exception {
        final var dir = Path.of(System.getProperty("user.home"), ".langgraph4j");

        final var ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:%s".formatted(dir.resolve("SQLiteSaverTest.db").toString()));


        return SQLiteSaverV2.builder()
                .datasource(ds)
                .stateSerializer(stateSerializer)
                .createTables(true)
                .build();
    }


}
