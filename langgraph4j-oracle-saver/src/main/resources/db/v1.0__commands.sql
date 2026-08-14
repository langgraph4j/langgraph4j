-- sqlDropTables
DROP INDEX IF EXISTS IDX_LANGRAPH4J_THREAD_NAME_RELEASED;
DROP TABLE IF EXISTS LANGRAPH4J_CHECKPOINT CASCADE CONSTRAINTS;
DROP TABLE IF EXISTS LANGRAPH4J_THREAD CASCADE CONSTRAINTS;

-- sqlUpsertThread
MERGE INTO LANGRAPH4J_THREAD existing
USING (SELECT ? AS THREAD_ID, ? AS THREAD_NAME, FALSE AS IS_RELEASED FROM DUAL) new
ON (existing.THREAD_NAME = new.THREAD_NAME AND existing.IS_RELEASED = new.IS_RELEASED)
WHEN NOT MATCHED THEN INSERT (THREAD_ID, THREAD_NAME, IS_RELEASED)
VALUES (new.THREAD_ID, new.THREAD_NAME, new.IS_RELEASED)

-- sqlInsertCheckpoint
INSERT INTO LANGRAPH4J_CHECKPOINT(checkpoint_id, thread_id, node_id, next_node_id, state_data)
SELECT ?, thread_id, ?, ?, ?
FROM LANGRAPH4J_THREAD
WHERE THREAD_NAME = ? AND IS_RELEASED = FALSE

-- sqlUpdateCheckpoint
UPDATE LANGRAPH4J_CHECKPOINT
SET
  checkpoint_id = ?,
  node_id = ?,
  next_node_id = ?,
  state_data = ?
WHERE checkpoint_id = ?

-- sqlSelectCheckpoints
SELECT
  c.checkpoint_id,
  c.node_id,
  c.next_node_id,
  c.state_data
FROM LANGRAPH4J_CHECKPOINT c
  INNER JOIN LANGRAPH4J_THREAD t ON c.thread_id = t.thread_id
WHERE t.thread_name = ? AND t.is_released != TRUE
ORDER BY c.saved_at DESC

-- sqlDeleteCheckpoint
DELETE FROM LANGRAPH4J_CHECKPOINT WHERE checkpoint_id = ?

-- sqlReleaseThread
UPDATE LANGRAPH4J_THREAD SET is_released = TRUE WHERE thread_name = ? AND is_released = FALSE
