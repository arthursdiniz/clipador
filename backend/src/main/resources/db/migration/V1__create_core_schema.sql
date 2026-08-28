CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

CREATE TABLE video (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    owner_id UUID REFERENCES app_user(id),
    source_type VARCHAR(20) NOT NULL,
    source_url VARCHAR(2048),
    original_filename VARCHAR(512),
    title VARCHAR(512),
    channel VARCHAR(255),
    duration_seconds NUMERIC(12,3),
    width INTEGER,
    height INTEGER,
    fps NUMERIC(8,3),
    video_codec VARCHAR(64),
    audio_codec VARCHAR(64),
    detected_language VARCHAR(20),
    storage_path VARCHAR(1024),
    thumbnail_url VARCHAR(2048),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_video_source_type CHECK (source_type IN ('YOUTUBE', 'UPLOAD')),
    CONSTRAINT ck_video_source CHECK (
        (source_type = 'YOUTUBE' AND source_url IS NOT NULL) OR
        (source_type = 'UPLOAD' AND original_filename IS NOT NULL)
    ),
    CONSTRAINT ck_video_duration CHECK (duration_seconds IS NULL OR duration_seconds > 0),
    CONSTRAINT ck_video_dimensions CHECK (
        (width IS NULL AND height IS NULL) OR (width > 0 AND height > 0)
    )
);

CREATE INDEX ix_video_owner_created ON video(owner_id, created_at DESC);
CREATE INDEX ix_video_created ON video(created_at DESC);

CREATE TABLE processing_job (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    video_id UUID NOT NULL REFERENCES video(id),
    status VARCHAR(40) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    current_stage VARCHAR(40) NOT NULL,
    error_code VARCHAR(100),
    error_message TEXT,
    correlation_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_job_correlation_id UNIQUE (correlation_id),
    CONSTRAINT uk_job_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_job_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_job_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_job_status CHECK (status IN (
        'RECEIVED', 'DOWNLOADING', 'DOWNLOADED', 'EXTRACTING_AUDIO',
        'TRANSCRIBING', 'TRANSCRIBED', 'ANALYZING', 'ANALYZED',
        'SELECTING_CLIPS', 'GENERATING_CLIPS', 'GENERATING_SUBTITLES',
        'RENDERING', 'COMPLETED', 'FAILED', 'CANCELLED'
    ))
);

CREATE INDEX ix_job_video_created ON processing_job(video_id, created_at DESC);
CREATE INDEX ix_job_status_created ON processing_job(status, created_at);

CREATE TABLE transcript (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    job_id UUID NOT NULL REFERENCES processing_job(id),
    detected_language VARCHAR(20) NOT NULL,
    language_probability NUMERIC(5,4),
    engine VARCHAR(100) NOT NULL,
    model_name VARCHAR(100),
    word_timestamps BOOLEAN NOT NULL DEFAULT FALSE,
    full_text TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_transcript_job UNIQUE (job_id),
    CONSTRAINT ck_transcript_language_probability CHECK (
        language_probability IS NULL OR language_probability BETWEEN 0 AND 1
    )
);

CREATE TABLE transcript_segment (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    transcript_id UUID NOT NULL REFERENCES transcript(id) ON DELETE CASCADE,
    segment_index INTEGER NOT NULL,
    start_time NUMERIC(12,3) NOT NULL,
    end_time NUMERIC(12,3) NOT NULL,
    text TEXT NOT NULL,
    confidence NUMERIC(5,4),
    speaker_label VARCHAR(100),
    words_json JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_transcript_segment_index UNIQUE (transcript_id, segment_index),
    CONSTRAINT ck_transcript_segment_time CHECK (start_time >= 0 AND end_time > start_time),
    CONSTRAINT ck_transcript_segment_confidence CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1)
);

CREATE INDEX ix_transcript_segment_time ON transcript_segment(transcript_id, start_time);

CREATE TABLE clip_candidate (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    job_id UUID NOT NULL REFERENCES processing_job(id),
    start_time NUMERIC(12,3) NOT NULL,
    end_time NUMERIC(12,3) NOT NULL,
    semantic_score NUMERIC(6,5) NOT NULL,
    audio_score NUMERIC(6,5) NOT NULL,
    visual_score NUMERIC(6,5) NOT NULL,
    narrative_score NUMERIC(6,5) NOT NULL,
    hook_score NUMERIC(6,5) NOT NULL,
    context_penalty NUMERIC(6,5) NOT NULL,
    final_score NUMERIC(6,5) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    hook VARCHAR(500),
    category VARCHAR(40) NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_candidate_window UNIQUE (job_id, start_time, end_time),
    CONSTRAINT ck_candidate_time CHECK (start_time >= 0 AND end_time > start_time),
    CONSTRAINT ck_candidate_semantic_score CHECK (semantic_score BETWEEN 0 AND 1),
    CONSTRAINT ck_candidate_audio_score CHECK (audio_score BETWEEN 0 AND 1),
    CONSTRAINT ck_candidate_visual_score CHECK (visual_score BETWEEN 0 AND 1),
    CONSTRAINT ck_candidate_narrative_score CHECK (narrative_score BETWEEN 0 AND 1),
    CONSTRAINT ck_candidate_hook_score CHECK (hook_score BETWEEN 0 AND 1),
    CONSTRAINT ck_candidate_context_penalty CHECK (context_penalty BETWEEN 0 AND 1),
    CONSTRAINT ck_candidate_final_score CHECK (final_score BETWEEN 0 AND 1)
);

CREATE INDEX ix_candidate_job_score ON clip_candidate(job_id, final_score DESC);
CREATE INDEX ix_candidate_selected ON clip_candidate(job_id, selected) WHERE selected = TRUE;

CREATE TABLE clip (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    job_id UUID NOT NULL REFERENCES processing_job(id),
    candidate_id UUID NOT NULL REFERENCES clip_candidate(id),
    format VARCHAR(40) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    duration_seconds NUMERIC(12,3) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    subtitle_path VARCHAR(1024),
    thumbnail_path VARCHAR(1024),
    render_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_clip_candidate UNIQUE (candidate_id),
    CONSTRAINT ck_clip_format CHECK (format IN ('VERTICAL_9_16', 'LANDSCAPE_16_9', 'SQUARE_1_1')),
    CONSTRAINT ck_clip_dimensions CHECK (width > 0 AND height > 0),
    CONSTRAINT ck_clip_duration CHECK (duration_seconds > 0)
);

CREATE INDEX ix_clip_job_created ON clip(job_id, created_at DESC);

CREATE TABLE processing_event (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    job_id UUID NOT NULL REFERENCES processing_job(id) ON DELETE CASCADE,
    from_status VARCHAR(40),
    to_status VARCHAR(40) NOT NULL,
    progress INTEGER NOT NULL,
    message VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_event_progress CHECK (progress BETWEEN 0 AND 100)
);

CREATE INDEX ix_event_job_occurred ON processing_event(job_id, occurred_at);

-- Transactional outbox: used from Phase 3 to publish messages without a DB/RabbitMQ dual-write gap.
CREATE TABLE outbox_message (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    message_type VARCHAR(100) NOT NULL,
    routing_key VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    CONSTRAINT ck_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX ix_outbox_unpublished ON outbox_message(occurred_at) WHERE published_at IS NULL;

