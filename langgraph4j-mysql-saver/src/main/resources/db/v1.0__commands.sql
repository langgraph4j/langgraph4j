
-- sqlDropTables
DROP TABLE IF EXISTS LANGRAPH4J_CHECKPOINT;
DROP TABLE IF EXISTS LANGRAPH4J_THREAD;

-- sqlSelectCheckpoints
SELECT
  c.checkpoint_id,
  c.node_id,
  c.next_node_id,
  c.state_data
FROM LANGRAPH4J_CHECKPOINT c
  INNER JOIN LANGRAPH4J_THREAD t ON c.thread_id = t.thread_id
WHERE t.thread_name = ? AND t.is_released != TRUE
ORDER BY c.saved_at DESC, c.id DESC

-- sqlUpsertThread
INSERT INTO LANGRAPH4J_THREAD (thread_id, thread_name, is_released)
VALUES (?, ?, FALSE)
ON DUPLICATE KEY UPDATE thread_id = thread_id

-- sqlInsertCheckpoint
INSERT INTO LANGRAPH4J_CHECKPOINT(checkpoint_id, thread_id, node_id, next_node_id, state_data)
SELECT ?, thread_id, ?, ?, ?
FROM LANGRAPH4J_THREAD
WHERE thread_name = ? AND is_released = FALSE

-- sqlUpdateCheckpoint
UPDATE LANGRAPH4J_CHECKPOINT
SET
    checkpoint_id = ?,
    node_id = ?,
    next_node_id = ?,
    state_data = ?
WHERE checkpoint_id = ?

-- sqlDeleteCheckpoint
DELETE FROM LANGRAPH4J_CHECKPOINT WHERE checkpoint_id = ?

-- sqlReleaseThread
UPDATE LANGRAPH4J_THREAD SET is_released = TRUE WHERE thread_name = ? AND is_released = FALSE


