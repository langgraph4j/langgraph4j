CREATE TABLE IF NOT EXISTS LANGRAPH4J_THREAD (
    thread_id BIGINT NOT NULL AUTO_INCREMENT,
    thread_name VARCHAR(255),
    is_released TINYINT NOT NULL DEFAULT 0,

    -- Used to emulate the SQLite partial unique index:
    -- UNIQUE(thread_name) WHERE is_released = 0
    unreleased_thread_name VARCHAR(255)
        GENERATED ALWAYS AS (
            CASE
                WHEN is_released = 0 THEN thread_name
                ELSE NULL
            END
        ) STORED,

    PRIMARY KEY (thread_id),

    CONSTRAINT chk_lg4jthread_is_released
        CHECK (is_released IN (0, 1)),

    UNIQUE KEY idx_unique_lg4jthread_thread_name_unreleased
        (unreleased_thread_name)
) ENGINE=InnoDB;


CREATE TABLE IF NOT EXISTS LANGRAPH4J_CHECKPOINT (
    checkpoint_id VARCHAR(255) NOT NULL,
    parent_checkpoint_id VARCHAR(255),
    thread_id BIGINT NOT NULL,
    node_id VARCHAR(255),
    next_node_id VARCHAR(255),

    state_data LONGTEXT NOT NULL,
    state_content_type VARCHAR(255),

    saved_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE KEY uk_lg4jcheckpoint_checkpoint_id (checkpoint_id),

    KEY idx_lg4jcheckpoint_thread_id (thread_id),

    KEY idx_lg4jcheckpoint_thread_id_saved_at_desc (
        thread_id,
        saved_at DESC,
        checkpoint_id DESC
    ),

    CONSTRAINT fk_thread
        FOREIGN KEY (thread_id)
        REFERENCES LANGRAPH4J_THREAD(thread_id)
) ENGINE=InnoDB;