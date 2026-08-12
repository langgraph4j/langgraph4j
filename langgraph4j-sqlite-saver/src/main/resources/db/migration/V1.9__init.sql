-- sqlCreateTables
CREATE TABLE IF NOT EXISTS LG4JThread (
    thread_id INTEGER PRIMARY KEY AUTOINCREMENT,
    thread_name TEXT UNIQUE NOT NULL,
    parent_thread_id INTEGER,
    is_interrupted INTEGER DEFAULT 0 NOT NULL CHECK (is_interrupted IN (0, 1)),
    message TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    FOREIGN KEY (parent_thread_id) REFERENCES LG4JThread(thread_id)
);

CREATE TABLE IF NOT EXISTS LG4JThreadTag (
    thread_id INTEGER PRIMARY KEY,
    thread_name TEXT,
    released_version INTEGER,
    parent_thread_id TEXT,
    is_released INTEGER DEFAULT 0 NOT NULL CHECK (is_released IN (0, 1)),
    is_error INTEGER DEFAULT 0 NOT NULL CHECK (is_error IN (0, 1)),
    message TEXT,
    created_at TEXT
);

CREATE TABLE IF NOT EXISTS LG4JCheckpoint (
    checkpoint_id TEXT NOT NULL UNIQUE,
    parent_checkpoint_id TEXT,
    thread_id INTEGER NOT NULL,
    node_id TEXT,
    next_node_id TEXT,
    state_data TEXT NOT NULL,
    state_content_type TEXT NOT NULL,
    saved_at TEXT DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id ON LG4JCheckpoint(thread_id);
CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id_saved_at_desc ON LG4JCheckpoint(thread_id, saved_at DESC);

