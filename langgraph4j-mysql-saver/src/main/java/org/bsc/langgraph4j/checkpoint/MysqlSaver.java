package org.bsc.langgraph4j.checkpoint;

import javax.sql.DataSource;

/**
 * <p>
 * MysqlSaver is an extension of MemorySaver that enables persistent,
 * reliable storage of workflow state in a MySQL database.
 * </p>
 * <p>
 * Two tables are used to store the workflow state:
 * 
 * <pre>
 *     CREATE TABLE LANGRAPH4J_THREAD (
 *          thread_id VARCHAR(36) PRIMARY KEY,
 *          thread_name VARCHAR(255),
 *          is_released BOOLEAN DEFAULT FALSE NOT NULL
 *     )
 *     CREATE UNIQUE INDEX IDX_LANGRAPH4J_THREAD_NAME_RELEASED
 *          ON LANGRAPH4J_THREAD(thread_name, is_released)
 *
 *     CREATE TABLE LANGRAPH4J_CHECKPOINT (
 *          checkpoint_id VARCHAR(36) PRIMARY KEY,
 *          thread_id VARCHAR(36) NOT NULL,
 *          node_id VARCHAR(255),
 *          next_node_id VARCHAR(255),
 *          state_data JSON NOT NULL,
 *          saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *
 *          CONSTRAINT LANGRAPH4J_FK_THREAD
 *              FOREIGN KEY(thread_id)
 *              REFERENCES LANGRAPH4J_THREAD(thread_id)
 *              ON DELETE CASCADE
 *     )
 * </pre>
 * </p>
 * <p>
 * A builder can be used to create an instance of MysqlSaver. The builder
 * allows to configure the following options:
 * - DataSource: indicates which data source should be used to connect
 * to the database
 * - CreateOption : indicates whether the tables should be created or
 * existing tables should be used.
 * </p>
 * <p>
 * Ex:
 * 
 * <pre>
 * var saver = MysqlSaver.builder()
 *         .createOption(CreateOption.CREATE_OR_REPLACE)
 *         .dataSource(DATA_SOURCE)
 *         .build();
 * </pre>
 * </p>
 */
public class MysqlSaver extends AbstractMysqlServer {

    /**
     * A builder for MysqlSaver.
     */
    public static class Builder extends AbstractBuilder<Builder> {

        /**
         * Creates a new instance of MysqlSaver
         *
         * @return the new instance of MysqlSaver.
         */
        public MysqlSaver build() throws Exception {
            return new MysqlSaver(this);
        }
    }

    /**
     * Private constructor used by the builder to create a new instance of
     * MysqlSaver.
     * 
     * @param builder   Builder instance
     */
    private MysqlSaver(Builder builder) throws Exception {
        super(builder);
    }

    /**
     * Creates an instance of a builder that allows to configure and create a new
     * instance of MysqlSaver.
     *
     * @return a new instance of the builder.
     */
    public static Builder builder() {
        return new Builder();
    }


}
