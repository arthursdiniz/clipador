package com.clipador.messaging.outbox;

import com.clipador.job.domain.ProcessingJob;
import com.clipador.messaging.RabbitTopology;
import com.clipador.messaging.contract.MediaValidationCommandV1;
import com.clipador.messaging.contract.ExtractAudioCommandV1;
import com.clipador.messaging.contract.MediaTaskTypes;
import com.clipador.messaging.contract.TranscribeAudioCommandV1;
import com.clipador.messaging.contract.AnalyzeContentCommandV1;
import com.clipador.config.ClipAnalysisProperties;
import com.clipador.config.RenderingProperties;
import com.clipador.clip.domain.ClipCandidate;
import com.clipador.messaging.contract.RenderClipsCommandV1;
import com.clipador.messaging.contract.RenderClipsCommandV2;
import java.util.List;
import com.clipador.video.domain.Video;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {
    private static final String VALIDATION_MESSAGE_TYPE = "MEDIA_VALIDATION_REQUESTED_V1";
    private static final String EXTRACTION_MESSAGE_TYPE = "AUDIO_EXTRACTION_REQUESTED_V1";
    private static final String TRANSCRIPTION_MESSAGE_TYPE = "AUDIO_TRANSCRIPTION_REQUESTED_V1";
    private static final String ANALYSIS_MESSAGE_TYPE = "CONTENT_ANALYSIS_REQUESTED_V1";
    private static final String RENDER_MESSAGE_TYPE = "CLIP_RENDERING_REQUESTED_V1";

    private final OutboxMessageRepository messages;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();
    private final ClipAnalysisProperties analysisProperties;
    private final RenderingProperties renderingProperties;

    public OutboxService(OutboxMessageRepository messages, ObjectMapper objectMapper,
                         ClipAnalysisProperties analysisProperties,
                         RenderingProperties renderingProperties) {
        this.messages = messages;
        this.objectMapper = objectMapper;
        this.analysisProperties = analysisProperties;
        this.renderingProperties = renderingProperties;
    }

    public UUID enqueueMediaValidation(ProcessingJob job, Video video, String storageKey) {
        Instant now = clock.instant();
        UUID messageId = UUID.randomUUID();
        var command = new MediaValidationCommandV1(
                MediaValidationCommandV1.VERSION,
                messageId,
                MediaValidationCommandV1.TASK_TYPE,
                job.getId(),
                video.getId(),
                job.getCorrelationId(),
                storageKey,
                job.getAttemptCount() + 1,
                now);
        save(job, messageId, VALIDATION_MESSAGE_TYPE, RabbitTopology.MEDIA_VALIDATE_KEY, command, now);
        return messageId;
    }

    public UUID enqueueAudioExtraction(ProcessingJob job) {
        Instant now = clock.instant();
        UUID messageId = UUID.randomUUID();
        String outputKey = "jobs/" + job.getId() + "/audio/normalized.wav";
        var command = new ExtractAudioCommandV1(1, messageId, MediaTaskTypes.EXTRACT_AUDIO,
                job.getId(), job.getVideo().getId(), job.getCorrelationId(),
                job.getVideo().getStoragePath(), outputKey, 16_000, 1,
                job.getAttemptCount() + 1, now);
        save(job, messageId, EXTRACTION_MESSAGE_TYPE, RabbitTopology.MEDIA_EXTRACT_AUDIO_KEY, command, now);
        return messageId;
    }

    public UUID enqueueTranscription(ProcessingJob job, String audioStorageKey) {
        Instant now = clock.instant();
        UUID messageId = UUID.randomUUID();
        String outputKey = "jobs/" + job.getId() + "/transcript/transcript.json";
        var command = new TranscribeAudioCommandV1(1, messageId, MediaTaskTypes.TRANSCRIBE_AUDIO,
                job.getId(), job.getVideo().getId(), job.getCorrelationId(), audioStorageKey,
                outputKey, whisperLanguageHint(job.getVideo().getDetectedLanguage()), true, true,
                job.getAttemptCount() + 1, now);
        save(job, messageId, TRANSCRIPTION_MESSAGE_TYPE, RabbitTopology.MEDIA_TRANSCRIBE_KEY, command, now);
        return messageId;
    }

    public UUID enqueueContentAnalysis(ProcessingJob job) {
        Instant now = clock.instant();
        UUID messageId = UUID.randomUUID();
        String outputKey = "jobs/" + job.getId() + "/analysis/candidates.json";
        var p = analysisProperties;
        var command = new AnalyzeContentCommandV1(1, messageId, MediaTaskTypes.ANALYZE_CONTENT,
                job.getId(), job.getVideo().getId(), job.getCorrelationId(),
                job.getVideo().getStoragePath(), job.getNormalizedAudioPath(),
                job.getTranscriptArtifactPath(), outputKey,
                p.minDurationSeconds(), p.idealDurationSeconds(), p.maxDurationSeconds(),
                p.maxCandidates(), p.semanticWeight(), p.audioWeight(), p.visualWeight(),
                p.narrativeWeight(), p.hookWeight(), p.contextPenaltyWeight(),
                job.getAttemptCount() + 1, now);
        save(job, messageId, ANALYSIS_MESSAGE_TYPE, RabbitTopology.MEDIA_ANALYZE_KEY, command, now);
        return messageId;
    }

    public UUID enqueueClipRendering(ProcessingJob job, List<ClipCandidate> selected) {
        Instant now = clock.instant();
        UUID messageId = UUID.randomUUID();
        String manifestKey = "jobs/" + job.getId() + "/render/manifest.json";
        var candidateSpecs = selected.stream().map(candidate -> new RenderClipsCommandV1.CandidateSpec(
                candidate.getId(), candidate.getStartTime().doubleValue(), candidate.getEndTime().doubleValue()))
                .toList();
        var formatSpecs = renderingProperties.formats().stream().map(format ->
                new RenderClipsCommandV1.FormatSpec(format, renderingProperties.width(format),
                        renderingProperties.height(format))).toList();
        var command = new RenderClipsCommandV2(2, messageId, MediaTaskTypes.RENDER_CLIPS,
                job.getId(), job.getVideo().getId(), job.getCorrelationId(),
                job.getVideo().getStoragePath(), job.getTranscriptArtifactPath(), manifestKey,
                candidateSpecs, formatSpecs, renderingProperties.burnInSubtitles(),
                renderingProperties.videoCrf(), renderingProperties.encoderPreset(),
                renderingProperties.audioBitrateKbps(), renderingProperties.outputFps(),
                renderingProperties.smartReframingEnabled(), renderingProperties.reframingMode(),
                renderingProperties.reframingSampleFps(), renderingProperties.reframingSmoothing(),
                renderingProperties.reframingMaxPanRatioPerSecond(),
                renderingProperties.reframingFaceMinSizeRatio(),
                renderingProperties.reframingDetectionWidth(),
                renderingProperties.reframingMaxKeyframes(),
                job.getAttemptCount() + 1, now);
        save(job, messageId, RENDER_MESSAGE_TYPE, RabbitTopology.MEDIA_RENDER_KEY, command, now);
        return messageId;
    }

    private void save(ProcessingJob job, UUID messageId, String messageType,
                      String routingKey, Object command, Instant now) {
        try {
            messages.save(OutboxMessage.pending(messageId, job.getId(), messageType,
                    routingKey, objectMapper.writeValueAsString(command), now));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize media task command", exception);
        }
    }

    private String whisperLanguageHint(String detectedLanguage) {
        if (detectedLanguage == null || detectedLanguage.isBlank()) return null;
        String normalized = detectedLanguage.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("pt")) return "pt";
        if (normalized.startsWith("en")) return "en";
        return null;
    }
}
