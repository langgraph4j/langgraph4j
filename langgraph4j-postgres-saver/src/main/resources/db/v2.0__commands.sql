-- sqlDropTables
DROP TABLE IF EXISTS LG4JCheckpoint CASCADE;
DROP TABLE IF EXISTS LG4JThread CASCADE;
DROP TABLE IF EXISTS LG4JThreadTag CASCADE;

-- sqlCheckThread
SELECT COUNT(*)
FROM LG4JThread
WHERE thread_name = ?

-- sqlSelectCheckpoints
WITH matched_thread AS (
    SELECT thread_id
    FROM LG4JThread
    WHERE thread_name = ?
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
WITH upserted AS (
    INSERT INTO LG4JThread (thread_name)
    VALUES (?)
    ON CONFLICT(thread_name) DO UPDATE
        SET thread_name = EXCLUDED.thread_name
    RETURNING thread_id
)
SELECT thread_id FROM upserted
UNION ALL
SELECT thread_id FROM LG4JThread
WHERE thread_name = ?
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

-- sqlReleaseThread_insertTag
INSERT INTO LG4JThreadTag (
    thread_id,
    thread_name,
    released_version,
    parent_thread_id,
    is_released,
    is_error,
    message,
    created_at
)
SELECT
    t.thread_id,
    t.thread_name,
    COALESCE(
        (
            SELECT MAX(tag.released_version)
            FROM LG4JThreadTag AS tag
            WHERE tag.thread_name = t.thread_name
        ),
        0
    ) + 1,
    t.parent_thread_id,
    TRUE,
    ?,
    ?,
    t.created_at
FROM LG4JThread AS t
WHERE t.thread_name = ?
RETURNING thread_id;

-- sqlReleaseThread_deleteThread
DELETE FROM LG4JThread WHERE thread_id = ?

-- sqlInterruptThread
UPDATE LG4JThread SET is_interrupted = TRUE, message = ? WHERE thread_name = ? AND is_interrupted = FALSE;

-- sqlSelectTag
SELECT
    t.thread_id,
    t.thread_name,
    t.released_version,
    t.parent_thread_id,
    t.is_released,
    t.is_error,
    t.message,
    t.created_at,
    c.checkpoint_id,
    c.node_id,
    c.next_node_id,
    c.state_data->>'binaryPayload' AS base64_data,
    c.state_content_type,
    c.parent_checkpoint_id
FROM LG4JThreadTag t
JOIN LG4JCheckpoint c ON c.thread_id = t.thread_id
WHERE %s thread_name = ? AND t.released_version = ?
ORDER BY c.saved_at DESC;

-- sqlSelectAllThreads
SELECT
    thread_id,
    thread_name,
    parent_thread_id,
    is_interrupted,
    message,
    created_at
FROM LG4JThread
ORDER BY created_at DESC;

-- sqlSelectAllTags
SELECT
    thread_id,
    thread_name,
    released_version,
    parent_thread_id,
    is_released,
    is_error,
    message,
    created_at
FROM LG4JThreadTag
ORDER BY created_at DESC;
