-- sqlCreateTables
CREATE TABLE IF NOT EXISTS LG4JThread (
    thread_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    thread_name VARCHAR(255) UNIQUE NOT NULL,
    parent_thread_id BIGINT,
    is_interrupted BOOLEAN DEFAULT FALSE NOT NULL,
    message TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (parent_thread_id) REFERENCES LG4JThread(thread_id)
);

CREATE TABLE IF NOT EXISTS LG4JThreadTag (
    thread_id BIGINT PRIMARY KEY,
    thread_name VARCHAR(255),
    released_version INTEGER,
    parent_thread_id BIGINT,
    is_released BOOLEAN DEFAULT FALSE NOT NULL,
    is_error BOOLEAN DEFAULT FALSE NOT NULL,
    message TEXT,
    created_at TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS LG4JCheckpoint (
    checkpoint_id VARCHAR(255) PRIMARY KEY,
    parent_checkpoint_id VARCHAR(255),
    thread_id BIGINT NOT NULL,
    node_id VARCHAR(255),
    next_node_id VARCHAR(255),
    state_data TEXT NOT NULL,
    state_content_type VARCHAR(255) NOT NULL,
    saved_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
);

