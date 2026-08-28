ALTER TABLE processing_job
    ADD COLUMN normalized_audio_path VARCHAR(1024),
    ADD COLUMN transcript_artifact_path VARCHAR(1024);
