package com.clipador.job.domain;

import com.clipador.shared.api.ConflictException;
import com.clipador.shared.domain.BaseEntity;
import com.clipador.video.domain.Video;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processing_job")
public class ProcessingJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private JobStatus status;

    @Column(nullable = false)
    private int progress;

    @Column(name = "current_stage", nullable = false, length = 40)
    private String currentStage;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "correlation_id", nullable = false, unique = true, length = 100)
    private String correlationId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 200)
    private String idempotencyKey;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "clip_quantity_mode", nullable = false, length = 20)
    private ClipQuantityMode clipQuantityMode;

    @Column(name = "requested_clip_count")
    private Integer requestedClipCount;

    @Column(name = "target_clip_count")
    private Integer targetClipCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "normalized_audio_path", length = 1024)
    private String normalizedAudioPath;

    @Column(name = "transcript_artifact_path", length = 1024)
    private String transcriptArtifactPath;

    @Column(name = "analysis_artifact_path", length = 1024)
    private String analysisArtifactPath;

    protected ProcessingJob() {}

    private ProcessingJob(Video video, String idempotencyKey, String correlationId,
                          ClipQuantityMode clipQuantityMode, Integer requestedClipCount) {
        this.video = video;
        this.status = JobStatus.RECEIVED;
        this.progress = 0;
        this.currentStage = JobStatus.RECEIVED.name();
        this.idempotencyKey = idempotencyKey;
        this.correlationId = correlationId;
        this.clipQuantityMode = clipQuantityMode == null ? ClipQuantityMode.AUTO : clipQuantityMode;
        this.requestedClipCount = requestedClipCount;
    }

    public static ProcessingJob received(Video video, String idempotencyKey, String correlationId) {
        return received(video, idempotencyKey, correlationId, ClipQuantityMode.AUTO, null);
    }

    public static ProcessingJob received(Video video, String idempotencyKey, String correlationId,
                                         ClipQuantityMode clipQuantityMode, Integer requestedClipCount) {
        return new ProcessingJob(video, idempotencyKey, correlationId, clipQuantityMode, requestedClipCount);
    }

    public void transitionTo(JobStatus nextStatus, int newProgress, Instant now) {
        if (!JobStateMachine.canTransition(status, nextStatus)) {
            throw new ConflictException("Cannot transition job from " + status + " to " + nextStatus);
        }
        if (newProgress < progress && nextStatus != JobStatus.RECEIVED) {
            throw new ConflictException("Job progress cannot decrease outside a retry");
        }
        if (newProgress < 0 || newProgress > 100) {
            throw new IllegalArgumentException("Job progress must be between 0 and 100");
        }
        status = nextStatus;
        currentStage = nextStatus.name();
        progress = nextStatus == JobStatus.COMPLETED ? 100 : newProgress;
        if (startedAt == null && nextStatus != JobStatus.RECEIVED) startedAt = now;
        if (nextStatus.isTerminal()) completedAt = now;
        if (nextStatus != JobStatus.FAILED) {
            errorCode = null;
            errorMessage = null;
        }
        if (nextStatus == JobStatus.RECEIVED) {
            attemptCount++;
            startedAt = null;
            completedAt = null;
        }
    }

    public void fail(String code, String message, Instant now) {
        transitionTo(JobStatus.FAILED, progress, now);
        errorCode = code == null ? "PROCESSING_FAILED" : code.substring(0, Math.min(code.length(), 100));
        errorMessage = message == null ? "Processing failed" : message.substring(0, Math.min(message.length(), 4000));
    }

    public void recordNormalizedAudio(String storagePath) {
        normalizedAudioPath = requiredStoragePath(storagePath);
    }

    public void recordTranscriptArtifact(String storagePath) {
        transcriptArtifactPath = requiredStoragePath(storagePath);
    }

    public void recordAnalysisArtifact(String storagePath) {
        analysisArtifactPath = requiredStoragePath(storagePath);
    }

    public void resolveTargetClipCount(int count) {
        if (count < 1 || count > 100) throw new IllegalArgumentException("Target clip count must be between 1 and 100");
        targetClipCount = count;
    }

    private String requiredStoragePath(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("storagePath is required");
        return value.substring(0, Math.min(value.length(), 1024));
    }

    public Video getVideo() { return video; }
    public JobStatus getStatus() { return status; }
    public int getProgress() { return progress; }
    public String getCurrentStage() { return currentStage; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getCorrelationId() { return correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public int getAttemptCount() { return attemptCount; }
    public ClipQuantityMode getClipQuantityMode() { return clipQuantityMode; }
    public Integer getRequestedClipCount() { return requestedClipCount; }
    public Integer getTargetClipCount() { return targetClipCount; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getNormalizedAudioPath() { return normalizedAudioPath; }
    public String getTranscriptArtifactPath() { return transcriptArtifactPath; }
    public String getAnalysisArtifactPath() { return analysisArtifactPath; }
}
