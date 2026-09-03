package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;

import java.util.LinkedList;
import java.util.Optional;

public abstract class AbstractSQLiteSaverV2 extends AbstractSQLiteSaver {

    public static final String THREAD_ROW_ID = "SQLITE_THREAD_ROW_ID";

    protected AbstractSQLiteSaverV2(AbstractBuilder<?> builder) throws Exception {
        super(builder);
    }

    public final RunnableConfig addThreadRowId(RunnableConfig config, int threadRowId) {
        return RunnableConfig.builder(config)
                .addMetadata(THREAD_ROW_ID, threadRowId)
                .build();
    }
    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) throws Exception {
        final var sqlSelectTag = sqlCommands.get("sqlSelectTag");

        final var threadRowId = config.metadata(THREAD_ROW_ID).map(v -> (Integer)v);

        final  var sqlSelectTag$ = threadRowId
                .map( id -> sqlSelectTag.formatted("thread_id = ? AND ") )
                .orElseGet( () -> sqlSelectTag.formatted(""));

        return exec(conn -> {
            final var checkpoints = new LinkedList<Checkpoint>();

            try (var ps = conn.prepareStatement( sqlSelectTag$)) {
                var index = 0;
                if( threadRowId.isPresent() ) {
                    ps.setInt(++index, threadRowId.get());
                }
                ps.setString(++index, threadId(config));
                if( version == null ) {
                    ps.setNull(++index, java.sql.Types.INTEGER);
                }
                else {
                    ps.setInt(++index, version);
                }
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {

                        final var checkpoint = new Checkpoint.Builder()
                                .id(rs.getString("checkpoint_id"))
                                .nodeId(rs.getString("node_id"))
                                .nextNodeId(rs.getString("next_node_id"))
                                .state(decodeState(rs.getString("state_data"), rs.getString("state_content_type")))
                                .build();

                        checkpoints.addLast(checkpoint);

                    }
                }
            }
            return Optional.of(new Tag(threadId(config), version, checkpoints));
        });
    }

}
