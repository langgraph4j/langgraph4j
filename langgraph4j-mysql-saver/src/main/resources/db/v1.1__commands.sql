-- sqlDropTables
DROP TABLE IF EXISTS LANGRAPH4J_CHECKPOINT;
DROP TABLE IF EXISTS LANGRAPH4J_THREAD;


-- sqlSelectCheckpoints
WITH matched_thread AS (
    SELECT thread_id
    FROM LANGRAPH4J_THREAD
    WHERE thread_name = ?
      AND is_released = 0
)
SELECT
    c.checkpoint_id,
    c.node_id,
    c.next_node_id,
    c.state_data,
    c.state_content_type,
    c.parent_checkpoint_id
FROM matched_thread t
JOIN LANGRAPH4J_CHECKPOINT c
    ON c.thread_id = t.thread_id
ORDER BY c.saved_at DESC;


-- sqlUpsertThread_insert
INSERT INTO LANGRAPH4J_THREAD (thread_name)
VALUES (?)
ON DUPLICATE KEY UPDATE
    thread_id = LAST_INSERT_ID(thread_id);

-- sqlUpsertThread_last_insert_id
SELECT LAST_INSERT_ID();


-- sqlInsertCheckpoint
INSERT INTO LANGRAPH4J_CHECKPOINT (
    checkpoint_id,
    parent_checkpoint_id,
    thread_id,
    node_id,
    next_node_id,
    state_data,
    state_content_type
)
VALUES (?, ?, ?, ?, ?, ?, ?);

-- sqlUpdateCheckpoint
UPDATE LANGRAPH4J_CHECKPOINT
SET
    checkpoint_id = ?,
    node_id = ?,
    next_node_id = ?,
    state_data = ?
WHERE checkpoint_id = ?

-- sqlDeletePreviousCheckpoint
DELETE FROM LANGRAPH4J_CHECKPOINT
WHERE checkpoint_id = ?;


-- sqlReleaseThread
UPDATE LANGRAPH4J_THREAD
SET is_released = 1
WHERE thread_name = ?
  AND is_released = 0;


-- sqlDeleteThread
DELETE FROM LANGRAPH4J_THREAD
WHERE thread_id = ?;