ALTER TABLE outbox_message
    ADD COLUMN next_attempt_at TIMESTAMPTZ;

DROP INDEX ix_outbox_unpublished;
CREATE INDEX ix_outbox_ready
    ON outbox_message(next_attempt_at, occurred_at)
    WHERE published_at IS NULL;

CREATE TABLE inbox_message (
    message_id UUID PRIMARY KEY,
    source VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_inbox_processed_at ON inbox_message(processed_at);

-- Existing ingestions are safely picked up when a development database is upgraded.
WITH ready_jobs AS (
    SELECT gen_random_uuid() AS message_id,
           j.id AS job_id,
           j.video_id,
           j.correlation_id,
           v.storage_path,
           CURRENT_TIMESTAMP AS created_at
      FROM processing_job j
      JOIN video v ON v.id = j.video_id
     WHERE j.status = 'DOWNLOADED'
       AND v.storage_path IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
             FROM outbox_message o
            WHERE o.aggregate_id = j.id
              AND o.message_type = 'MEDIA_VALIDATION_REQUESTED_V1'
       )
)
INSERT INTO outbox_message (
    id, aggregate_type, aggregate_id, message_type, routing_key,
    payload, occurred_at, next_attempt_at
)
SELECT message_id,
       'ProcessingJob',
       job_id,
       'MEDIA_VALIDATION_REQUESTED_V1',
       'media.validate',
       jsonb_build_object(
           'schemaVersion', 1,
           'messageId', message_id,
           'taskType', 'VALIDATE_MEDIA',
           'jobId', job_id,
           'videoId', video_id,
           'correlationId', correlation_id,
           'storageKey', storage_path,
           'attempt', 1,
           'createdAt', created_at
       ),
       created_at,
       created_at
  FROM ready_jobs;
