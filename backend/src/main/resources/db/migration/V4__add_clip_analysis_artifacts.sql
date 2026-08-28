ALTER TABLE processing_job
    ADD COLUMN analysis_artifact_path VARCHAR(1024);

ALTER TABLE clip_candidate
    ADD COLUMN analysis_key VARCHAR(64),
    ADD COLUMN source_text TEXT;

CREATE UNIQUE INDEX uk_candidate_analysis_key
    ON clip_candidate(job_id, analysis_key)
    WHERE analysis_key IS NOT NULL;
