package com.clipador.messaging.contract;

import com.clipador.clip.domain.ClipFormat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RenderClipsCommandV1(
        int schemaVersion,
        UUID messageId,
        String taskType,
        UUID jobId,
        UUID videoId,
        String correlationId,
        String videoStorageKey,
        String transcriptStorageKey,
        String manifestStorageKey,
        List<CandidateSpec> candidates,
        List<FormatSpec> formats,
        boolean burnInSubtitles,
        int videoCrf,
        String encoderPreset,
        int audioBitrateKbps,
        int outputFps,
        int attempt,
        Instant createdAt) {

    public RenderClipsCommandV1 {
        if (schemaVersion != 1 || !MediaTaskTypes.RENDER_CLIPS.equals(taskType)) {
            throw new IllegalArgumentException("Unsupported rendering contract");
        }
        if (messageId == null || jobId == null || videoId == null || createdAt == null
                || blank(correlationId) || blank(videoStorageKey) || blank(transcriptStorageKey)
                || blank(manifestStorageKey) || attempt < 1) {
            throw new IllegalArgumentException("Rendering identifiers and storage keys are required");
        }
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        formats = formats == null ? List.of() : List.copyOf(formats);
        if (candidates.isEmpty() || candidates.size() > 100 || formats.isEmpty() || formats.size() > 3) {
            throw new IllegalArgumentException("Rendering candidates or formats are invalid");
        }
        if (videoCrf < 16 || videoCrf > 32 || audioBitrateKbps < 64 || audioBitrateKbps > 320
                || outputFps < 24 || outputFps > 60 || blank(encoderPreset)) {
            throw new IllegalArgumentException("Rendering codec options are invalid");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record CandidateSpec(UUID candidateId, double start, double end) {
        public CandidateSpec {
            if (candidateId == null || !Double.isFinite(start) || !Double.isFinite(end)
                    || start < 0 || end <= start || end - start > 180) {
                throw new IllegalArgumentException("Render candidate is invalid");
            }
        }
    }

    public record FormatSpec(ClipFormat format, int width, int height) {
        public FormatSpec {
            if (format == null || width < 240 || height < 240 || width > 3840 || height > 3840
                    || width % 2 != 0 || height % 2 != 0) {
                throw new IllegalArgumentException("Render format is invalid");
            }
        }
    }
}
