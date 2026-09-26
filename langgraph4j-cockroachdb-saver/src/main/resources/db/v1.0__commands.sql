-- sqlDropTables
DROP TABLE IF EXISTS LG4JCheckpoint CASCADE;
DROP TABLE IF EXISTS LG4JThread CASCADE;

-- sqlCheckThread
SELECT COUNT(*)
FROM LG4JThread
WHERE thread_name = ? AND is_released = FALSE

-- sqlSelectCheckpoints
WITH matched_thread AS (
    SELECT thread_id
    FROM LG4JThread
    WHERE thread_name = ? AND is_released = FALSE
)
SELECT  c.checkpoint_id,
        c.node_id,
        c.next_node_id,
        c.state_data->>'binaryPayload' AS base64_data,
        c.state_content_type,
        c.parent_checkpoint_id
FROM matched_thread t
JOIN LG4JCheckpoint c ON c.thread_id = t.thread_id
ORDER BY c.saved_at DESC

-- sqlUpsertThread
WITH inserted AS (
    INSERT INTO LG4JThread (thread_id, thread_name, is_released)
    VALUES (?, ?, FALSE)
    ON CONFLICT (thread_name)
    WHERE is_released = FALSE
    DO NOTHING
    RETURNING thread_id
)
SELECT thread_id FROM inserted
UNION ALL
SELECT thread_id FROM LG4JThread
WHERE thread_name = ? AND is_released = FALSE
LIMIT 1;

-- sqlInsertCheckpoint
INSERT INTO LG4JCheckpoint(
checkpoint_id,
parent_checkpoint_id,
thread_id,
node_id,
next_node_id,
state_data,
state_content_type)
VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)

-- sqlDeletePreviousCheckpoint
DELETE FROM LG4JCheckpoint
WHERE checkpoint_id = ?;

-- sqlSelectThread
SELECT thread_id FROM LG4JThread
WHERE thread_name = ? AND is_released = FALSE

-- sqlReleaseThread
UPDATE LG4JThread
SET
    is_released = TRUE
WHERE thread_id = ?;
