package com.clipador.api;

import com.clipador.clip.domain.Clip;
import com.clipador.clip.domain.ClipCandidate;
import com.clipador.clip.domain.ClipCategory;
import com.clipador.clip.domain.ClipFormat;
import com.clipador.job.domain.JobStatus;
import com.clipador.job.domain.ClipQuantityMode;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.video.domain.Video;
import com.clipador.video.domain.VideoSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class ApiModels {
    private ApiModels() {}

    public record YoutubeVideoRequest(
            @NotBlank @Size(max = 2048) String url,
            @Size(max = 512) String title,
            ClipQuantityMode clipQuantityMode,
            Integer clipCount) {
        public YoutubeVideoRequest {
            if (clipQuantityMode == null) clipQuantityMode = ClipQuantityMode.AUTO;
        }
    }

    public record RegistrationResponse(UUID videoId, UUID jobId, JobStatus status,
                                       String correlationId, boolean created) {}

    public record VideoResponse(UUID id, VideoSourceType sourceType, String sourceUrl,
                                String originalFilename, String title, String channel,
                                BigDecimal durationSeconds, Integer width, Integer height,
                                BigDecimal fps, String videoCodec, String audioCodec,
                                String detectedLanguage, String thumbnailUrl,
                                Instant createdAt, Instant updatedAt) {
        static VideoResponse from(Video video) {
            return new VideoResponse(video.getId(), video.getSourceType(), video.getSourceUrl(),
                    video.getOriginalFilename(), video.getTitle(), video.getChannel(),
                    video.getDurationSeconds(), video.getWidth(), video.getHeight(), video.getFps(),
                    video.getVideoCodec(), video.getAudioCodec(), video.getDetectedLanguage(),
                    video.getThumbnailUrl(), video.getCreatedAt(), video.getUpdatedAt());
        }
    }

    public record JobResponse(UUID id, UUID videoId, JobStatus status, int progress,
                              String currentStage, String errorCode, String errorMessage,
                              String correlationId, int attemptCount,
                              ClipQuantityMode clipQuantityMode, Integer requestedClipCount,
                              Integer targetClipCount, Instant startedAt,
                              Instant completedAt, String normalizedAudioPath,
                              String transcriptArtifactPath, String analysisArtifactPath,
                              Instant createdAt, Instant updatedAt) {
        static JobResponse from(ProcessingJob job) {
            return new JobResponse(job.getId(), job.getVideo().getId(), job.getStatus(), job.getProgress(),
                    job.getCurrentStage(), job.getErrorCode(), job.getErrorMessage(), job.getCorrelationId(),
                    job.getAttemptCount(), job.getClipQuantityMode(), job.getRequestedClipCount(),
                    job.getTargetClipCount(), job.getStartedAt(), job.getCompletedAt(),
                    job.getNormalizedAudioPath(), job.getTranscriptArtifactPath(),
                    job.getAnalysisArtifactPath(),
                    job.getCreatedAt(), job.getUpdatedAt());
        }
    }

    public record ClipCandidateResponse(
            UUID id, UUID jobId, BigDecimal startTime, BigDecimal endTime,
            BigDecimal semanticScore, BigDecimal audioScore, BigDecimal visualScore,
            BigDecimal narrativeScore, BigDecimal hookScore, BigDecimal contextPenalty,
            BigDecimal finalScore, String reason, String hook, String title, ClipCategory category,
            boolean selected, String sourceText, Instant createdAt) {
        static ClipCandidateResponse from(ClipCandidate candidate) {
            return new ClipCandidateResponse(candidate.getId(), candidate.getJob().getId(),
                    candidate.getStartTime(), candidate.getEndTime(), candidate.getSemanticScore(),
                    candidate.getAudioScore(), candidate.getVisualScore(), candidate.getNarrativeScore(),
                    candidate.getHookScore(), candidate.getContextPenalty(), candidate.getFinalScore(),
                    candidate.getReason(), candidate.getHook(), candidate.getTitle(), candidate.getCategory(), candidate.isSelected(),
                    candidate.getSourceText(), candidate.getCreatedAt());
        }
    }

    public record JobProgressResponse(UUID jobId, JobStatus status, int progress, String currentStage,
                                      String errorCode, String errorMessage,
                                      ClipQuantityMode clipQuantityMode, Integer requestedClipCount,
                                      Integer targetClipCount, Instant updatedAt) {
        static JobProgressResponse from(ProcessingJob job) {
            return new JobProgressResponse(job.getId(), job.getStatus(), job.getProgress(),
                    job.getCurrentStage(), job.getErrorCode(), job.getErrorMessage(),
                    job.getClipQuantityMode(), job.getRequestedClipCount(),
                    job.getTargetClipCount(), job.getUpdatedAt());
        }
    }

    public record ClipResponse(UUID id, UUID jobId, UUID candidateId, String title, ClipFormat format,
                               int width, int height, BigDecimal durationSeconds,
                               String subtitlePath, String srtPath, String vttPath, String assPath,
                               String thumbnailPath, String renderError,
                               Instant createdAt) {
        static ClipResponse from(Clip clip) {
            return new ClipResponse(clip.getId(), clip.getJob().getId(), clip.getCandidate().getId(),
                    clip.getCandidate().getTitle(),
                    clip.getFormat(), clip.getWidth(), clip.getHeight(), clip.getDurationSeconds(),
                    clip.getSubtitlePath(), clip.getSrtPath(), clip.getVttPath(), clip.getAssPath(),
                    clip.getThumbnailPath(), clip.getRenderError(), clip.getCreatedAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
                                  int totalPages, boolean first, boolean last) {
        static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
        }
    }
}
