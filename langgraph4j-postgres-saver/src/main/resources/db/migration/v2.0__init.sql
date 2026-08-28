-- sqlCreateTables
CREATE TABLE IF NOT EXISTS LG4JThread (
    thread_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    thread_name VARCHAR(255) UNIQUE NOT NULL,
    parent_thread_id BIGINT,
    is_interrupted BOOLEAN DEFAULT FALSE NOT NULL,
    message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS LG4JThreadTag (
    thread_id BIGINT PRIMARY KEY,
    thread_name VARCHAR(255),
    released_version INTEGER,
    parent_thread_id BIGINT,
    is_released BOOLEAN DEFAULT FALSE NOT NULL,
    is_error BOOLEAN DEFAULT FALSE NOT NULL,
    message TEXT,
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS LG4JCheckpoint (
    checkpoint_id UUID PRIMARY KEY,
    parent_checkpoint_id UUID,
    thread_id BIGINT NOT NULL,
    node_id VARCHAR(255),
    next_node_id VARCHAR(255),
    state_data JSONB NOT NULL,
    state_content_type VARCHAR(100) NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP

/*
    CONSTRAINT fk_thread
        FOREIGN KEY(thread_id)
        REFERENCES LG4JThread(thread_id)
        ON DELETE CASCADE
*/
);

CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id ON LG4JCheckpoint(thread_id);
CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id_saved_at_desc ON LG4JCheckpoint(thread_id, saved_at DESC);
CREATE INDEX IF NOT EXISTS idx_lg4jthreadtag_thread_name_released_version ON LG4JThreadTag(thread_name, released_version DESC);
