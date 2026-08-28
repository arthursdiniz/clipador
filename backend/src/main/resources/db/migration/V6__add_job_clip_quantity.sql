ALTER TABLE processing_job
    ADD COLUMN clip_quantity_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    ADD COLUMN requested_clip_count INTEGER,
    ADD COLUMN target_clip_count INTEGER;

ALTER TABLE processing_job
    ADD CONSTRAINT ck_job_clip_quantity_mode
        CHECK (clip_quantity_mode IN ('AUTO', 'EXTENDED', 'MANUAL')),
    ADD CONSTRAINT ck_job_requested_clip_count
        CHECK (
            (clip_quantity_mode = 'MANUAL' AND requested_clip_count BETWEEN 1 AND 100)
            OR (clip_quantity_mode IN ('AUTO', 'EXTENDED') AND requested_clip_count IS NULL)
        ),
    ADD CONSTRAINT ck_job_target_clip_count
        CHECK (target_clip_count IS NULL OR target_clip_count BETWEEN 1 AND 100);
