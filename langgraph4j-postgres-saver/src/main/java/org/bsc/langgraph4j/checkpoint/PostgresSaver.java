package org.bsc.langgraph4j.checkpoint;

import java.sql.SQLException;

/**
 * Postgres checkpoint saver.
 */
public class PostgresSaver extends AbstractPostgresSaver {

    public static class Builder extends AbstractBuilder<Builder> {

        public PostgresSaver build() throws Exception {
            validate();
            return new PostgresSaver(this);
        }
    }

    protected PostgresSaver(Builder builder) throws Exception {
        super(builder);
    }

    public static Builder builder() {
        return new Builder();
    }
}
