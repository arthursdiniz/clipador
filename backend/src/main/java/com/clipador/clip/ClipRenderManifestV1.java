package com.clipador.clip;

import java.util.List;
import java.util.UUID;

public record ClipRenderManifestV1(
        int schemaVersion,
        UUID jobId,
        UUID videoId,
        List<Render> renders) {

    public record Render(
            UUID candidateId,
            String format,
            String status,
            int width,
            int height,
            double durationSeconds,
            String storageKey,
            String srtStorageKey,
            String vttStorageKey,
            String assStorageKey,
            String thumbnailStorageKey,
            String errorCode,
            String errorMessage,
            Reframing reframing) {}

    public record Reframing(
            String strategy,
            double faceDetectionCoverage,
            double subjectDetectionCoverage,
            int keyframeCount,
            boolean fallback) {}
}
