-- sqlCreateTables
CREATE TABLE IF NOT EXISTS LG4JThread (
     thread_id UUID PRIMARY KEY,
     thread_name VARCHAR(255),
     is_released BOOLEAN DEFAULT FALSE NOT NULL
 );

 CREATE TABLE IF NOT EXISTS LG4JCheckpoint (
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

 CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id ON LG4JCheckpoint(thread_id);
 CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id_saved_at_desc ON LG4JCheckpoint(thread_id, saved_at DESC);
 CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_lg4jthread_thread_name_unreleased  ON LG4JThread(thread_name) WHERE is_released = FALSE;
