-- sqlCreateTables
CREATE TABLE IF NOT EXISTS LG4JThread (
    thread_id INTEGER PRIMARY KEY AUTOINCREMENT,
    thread_name TEXT,
    is_released INTEGER DEFAULT 0 NOT NULL CHECK (is_released IN (0, 1))
);

CREATE TABLE IF NOT EXISTS LG4JCheckpoint (
    checkpoint_id TEXT NOT NULL UNIQUE,
    parent_checkpoint_id TEXT,
    thread_id INTEGER NOT NULL,
    node_id TEXT,
    next_node_id TEXT,
    state_data TEXT NOT NULL,
    state_content_type TEXT NOT NULL,
    saved_at TEXT DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),

    CONSTRAINT fk_thread
        FOREIGN KEY(thread_id)
        REFERENCES LG4JThread(thread_id)
);

CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id ON LG4JCheckpoint(thread_id);
CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id_saved_at_desc ON LG4JCheckpoint(thread_id, saved_at DESC, checkpoint_id DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_lg4jthread_thread_name_unreleased
    ON LG4JThread(thread_name)
    WHERE is_released = 0;
