-- sqlDropTables
DROP TABLE LG4JCheckpoint CASCADE CONSTRAINTS;
DROP TABLE LG4JThreadTag CASCADE CONSTRAINTS;
DROP TABLE LG4JThread CASCADE CONSTRAINTS;

-- sqlSelectCheckpoints
WITH matched_thread AS (
    SELECT thread_id
    FROM LG4JThread
    WHERE thread_name = ?
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
MERGE INTO LG4JThread target
USING (SELECT ? AS ignored_thread_id, ? AS thread_name FROM DUAL) source
ON (target.thread_name = source.thread_name)
WHEN NOT MATCHED THEN
    INSERT (thread_name)
    VALUES (source.thread_name)

-- sqlInsertCheckpoint
INSERT INTO LG4JCheckpoint(
    checkpoint_id,
    parent_checkpoint_id,
    thread_id,
    node_id,
    next_node_id,
    state_data,
    state_content_type)
SELECT ?, NULL, thread_id, ?, ?, ?, ?
FROM LG4JThread
WHERE thread_name = ?

-- sqlUpdateCheckpoint
UPDATE LG4JCheckpoint
SET
    checkpoint_id = ?,
    parent_checkpoint_id = NULL,
    node_id = ?,
    next_node_id = ?,
    state_data = ?,
    state_content_type = ?
WHERE checkpoint_id = ?

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
            FROM LG4JThreadTag tag
            WHERE tag.thread_name = t.thread_name
        ),
        0
    ) + 1,
    t.parent_thread_id,
    1,
    ?,
    ?,
    t.created_at
FROM LG4JThread t
WHERE t.thread_name = ?

-- sqlReleaseThread_deleteThread
DELETE FROM LG4JThread WHERE thread_id = ?

-- sqlInterruptThread
UPDATE LG4JThread SET is_interrupted = 1, message = ? WHERE thread_name = ? AND is_interrupted = 0

-- sqlEnableForeignKeys
SELECT 1 FROM DUAL

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
    c.state_data,
    c.state_content_type,
    c.parent_checkpoint_id
FROM LG4JThreadTag t
JOIN LG4JCheckpoint c ON c.thread_id = t.thread_id
WHERE %s t.thread_name = ? AND t.released_version = ?
ORDER BY c.saved_at DESC

-- sqlSelectAllThreads
SELECT
    thread_id,
    thread_name,
    parent_thread_id,
    is_interrupted,
    message,
    created_at
FROM LG4JThread
ORDER BY created_at DESC

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
ORDER BY created_at DESC
