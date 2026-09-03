package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;

import java.util.LinkedList;
import java.util.Optional;

public abstract class AbstractPostgresSaverV2 extends AbstractPostgresSaver {

    public static final String POSTGRES_THREAD_ROW_ID = "POSTGRES_THREAD_ROW_ID";

    protected AbstractPostgresSaverV2(AbstractBuilder<?> builder) throws Exception {
        super(builder);
    }

    public final RunnableConfig addThreadRowId(RunnableConfig config, long threadRowId) {
        return RunnableConfig.builder(config)
                .addMetadata(POSTGRES_THREAD_ROW_ID, threadRowId)
                .build();
    }


    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) throws Exception {
        final var sqlSelectTag = sqlCommands.get("sqlSelectTag");

        final var threadRowId = config.metadata(POSTGRES_THREAD_ROW_ID).map(v -> (Number) v);

        final var sqlSelectTag$ = threadRowId
                .map(id -> sqlSelectTag.formatted("t.thread_id = ? AND"))
                .orElseGet(() -> sqlSelectTag.formatted(""));

        return execTransaction(conn -> {
            final var checkpoints = new LinkedList<Checkpoint>();

            try (var ps = conn.prepareStatement(sqlSelectTag$)) {
                var index = 0;
                if (threadRowId.isPresent()) {
                    ps.setLong(++index, threadRowId.get().longValue());
                }
                ps.setString(++index, threadId(config));
                if (version == null) {
                    ps.setNull(++index, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(++index, version);
                }
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        final var checkpoint = new Checkpoint.Builder()
                                .id(rs.getString("checkpoint_id"))
                                .nodeId(rs.getString("node_id"))
                                .nextNodeId(rs.getString("next_node_id"))
                                .state(decodeState(rs.getBytes("base64_data"), rs.getString("state_content_type")))
                                .build();

                        checkpoints.addLast(checkpoint);
                    }
                }
            }
            return Optional.of(new Tag(threadId(config), version, checkpoints));
        });
    }
}
