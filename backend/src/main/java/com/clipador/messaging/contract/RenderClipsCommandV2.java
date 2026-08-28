package com.clipador.messaging.contract;

import com.clipador.config.ReframingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RenderClipsCommandV2(
        int schemaVersion,
        UUID messageId,
        String taskType,
        UUID jobId,
        UUID videoId,
        String correlationId,
        String videoStorageKey,
        String transcriptStorageKey,
        String manifestStorageKey,
        List<RenderClipsCommandV1.CandidateSpec> candidates,
        List<RenderClipsCommandV1.FormatSpec> formats,
        boolean burnInSubtitles,
        int videoCrf,
        String encoderPreset,
        int audioBitrateKbps,
        int outputFps,
        boolean smartReframingEnabled,
        ReframingMode reframingMode,
        double reframingSampleFps,
        double reframingSmoothing,
        double reframingMaxPanRatioPerSecond,
        double reframingFaceMinSizeRatio,
        int reframingDetectionWidth,
        int reframingMaxKeyframes,
        int attempt,
        Instant createdAt) {

    public RenderClipsCommandV2 {
        if (schemaVersion != 2 || !MediaTaskTypes.RENDER_CLIPS.equals(taskType)) {
            throw new IllegalArgumentException("Unsupported smart rendering contract");
        }
        new RenderClipsCommandV1(1, messageId, taskType, jobId, videoId, correlationId,
                videoStorageKey, transcriptStorageKey, manifestStorageKey, candidates, formats,
                burnInSubtitles, videoCrf, encoderPreset, audioBitrateKbps, outputFps, attempt, createdAt);
        if (reframingMode == null || reframingSampleFps < .25 || reframingSampleFps > 5
                || reframingSmoothing < 0 || reframingSmoothing > .98
                || reframingMaxPanRatioPerSecond < .05 || reframingMaxPanRatioPerSecond > 1
                || reframingFaceMinSizeRatio < .005 || reframingFaceMinSizeRatio > .25
                || reframingDetectionWidth < 160 || reframingDetectionWidth > 1280
                || reframingMaxKeyframes < 2 || reframingMaxKeyframes > 256) {
            throw new IllegalArgumentException("Smart reframing options are invalid");
        }
        candidates = List.copyOf(candidates);
        formats = List.copyOf(formats);
    }
}
