ALTER TABLE clip DROP CONSTRAINT uk_clip_candidate;
ALTER TABLE clip ALTER COLUMN storage_path DROP NOT NULL;
ALTER TABLE clip ADD COLUMN srt_path VARCHAR(1024);
ALTER TABLE clip ADD COLUMN vtt_path VARCHAR(1024);
ALTER TABLE clip ADD COLUMN ass_path VARCHAR(1024);
UPDATE clip SET render_error = NULL WHERE storage_path IS NOT NULL AND render_error IS NOT NULL;
ALTER TABLE clip ADD CONSTRAINT uk_clip_candidate_format UNIQUE (candidate_id, format);
ALTER TABLE clip ADD CONSTRAINT ck_clip_render_outcome CHECK (
    (storage_path IS NOT NULL AND render_error IS NULL)
    OR (storage_path IS NULL AND render_error IS NOT NULL)
);
