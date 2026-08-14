-- sqlDropTables
DROP TABLE IF EXISTS LG4JCheckpoint;
DROP TABLE IF EXISTS LG4JThread;

-- sqlQueryCheckpoints
WITH matched_thread AS (
    SELECT thread_id
    FROM LG4JThread
    WHERE thread_name = ? AND is_released = 0
)
SELECT  c.checkpoint_id,
        c.node_id,
        c.next_node_id,
        c.state_data,
        c.state_content_type,
        c.parent_checkpoint_id
FROM matched_thread t
JOIN LG4JCheckpoint c ON c.thread_id = t.thread_id
ORDER BY c.saved_at DESC

-- sqlUpsertThread
INSERT INTO LG4JThread (thread_name)
VALUES (?)
ON CONFLICT(thread_name) WHERE is_released = 0
DO UPDATE SET thread_name = excluded.thread_name
RETURNING thread_id;

-- sqlInsertCheckpoint
INSERT INTO LG4JCheckpoint(
    checkpoint_id,
    parent_checkpoint_id,
    thread_id,
    node_id,
    next_node_id,
    state_data,
    state_content_type)
VALUES (?, ?, ?, ?, ?, ?, ?)

-- sqlDeletePreviousCheckpoint
DELETE FROM LG4JCheckpoint WHERE checkpoint_id = ?;

-- sqlReleaseThread
UPDATE LG4JThread
SET is_released = 1
WHERE thread_name = ? AND is_released = 0
-- RETURNING thread_id;

-- sqlReleaseThread_deleteThread
DELETE FROM LG4JThread WHERE thread_id = ?

-- sqlEnableForeignKeys
PRAGMA foreign_keys = ON
