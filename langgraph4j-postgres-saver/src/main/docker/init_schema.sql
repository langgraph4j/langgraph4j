-- Drop tables in reverse order of dependency if they exist, using CASCADE for FKs
DROP TABLE IF EXISTS LG4JCheckpoint CASCADE;
DROP TABLE IF EXISTS LG4JThread CASCADE;

CREATE TABLE LG4JThread (
    thread_id UUID PRIMARY KEY,
    thread_name VARCHAR(255),
    is_released BOOLEAN DEFAULT FALSE NOT NULL
--    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
--    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE LG4JCheckpoint (
    checkpoint_id UUID PRIMARY KEY,
    parent_checkpoint_id UUID,
    thread_id UUID NOT NULL,
    node_id VARCHAR(255),
    next_node_id VARCHAR(255),
    state_data JSONB NOT NULL,
    state_content_type VARCHAR(100) NOT NULL, -- New field for content type
    saved_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_thread
        FOREIGN KEY(thread_id)
        REFERENCES LG4JThread(thread_id)
        ON DELETE CASCADE
);

-- Indexes for common query patterns
CREATE INDEX idx_lg4jcheckpoint_thread_id ON LG4JCheckpoint(thread_id);

-- Useful for fetching the latest checkpoint(s) for a thread
CREATE INDEX idx_lg4jcheckpoint_thread_id_saved_at_desc ON LG4JCheckpoint(thread_id, saved_at DESC);

-- Index to optimize search for thread_name where is_released is FALSE
-- CREATE INDEX idx_lg4jthread_thread_name_unreleased ON LG4JThread (thread_name) WHERE is_released = FALSE;

CREATE UNIQUE INDEX idx_unique_lg4jthread_thread_name_unreleased  ON LG4JThread(thread_name) WHERE is_released = FALSE;

-- Optional: Index on is_released if you frequently query for open/released threads
--CREATE INDEX idx_threads_is_released ON threads(is_released);

--CREATE OR REPLACE FUNCTION update_thread_update_time()
--RETURNS TRIGGER AS $$
--BEGIN
--    UPDATE LG4JThread
--    SET updated_at = NOW()
--    WHERE thread_id = NEW.thread_id;
--    RETURN NEW;
--END;
--$$ LANGUAGE plpgsql;
--
--CREATE TRIGGER trigger_update_thread_time_on_checkpoint_insert
--AFTER INSERT ON LG4JCheckpoint
--FOR EACH ROW
--EXECUTE FUNCTION update_thread_update_time();