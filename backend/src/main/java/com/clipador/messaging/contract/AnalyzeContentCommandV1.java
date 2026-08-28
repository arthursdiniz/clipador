package com.clipador.messaging.contract;

import java.time.Instant;
import java.util.UUID;

public record AnalyzeContentCommandV1(
        int schemaVersion,
        UUID messageId,
        String taskType,
        UUID jobId,
        UUID videoId,
        String correlationId,
        String videoStorageKey,
        String audioStorageKey,
        String transcriptStorageKey,
        String analysisStorageKey,
        String videoTitle,
        String videoChannel,
        double minDurationSeconds,
        double idealDurationSeconds,
        double maxDurationSeconds,
        int maxCandidates,
        double semanticWeight,
        double audioWeight,
        double visualWeight,
        double narrativeWeight,
        double hookWeight,
        double contextPenaltyWeight,
        int attempt,
        Instant createdAt
) {
    public AnalyzeContentCommandV1 {
        if (schemaVersion != 1 || !MediaTaskTypes.ANALYZE_CONTENT.equals(taskType)) {
            throw new IllegalArgumentException("Unsupported content analysis contract");
        }
        if (messageId == null || jobId == null || videoId == null || createdAt == null
                || correlationId == null || correlationId.isBlank() || attempt < 1) {
            throw new IllegalArgumentException("Analysis identifiers, correlation and attempt are required");
        }
        if (blank(videoStorageKey) || blank(audioStorageKey) || blank(transcriptStorageKey)
                || blank(analysisStorageKey)) {
            throw new IllegalArgumentException("Analysis storage keys are required");
        }
        videoTitle = clean(videoTitle, 512);
        videoChannel = clean(videoChannel, 255);
        if (minDurationSeconds < 5 || idealDurationSeconds < minDurationSeconds
                || maxDurationSeconds < idealDurationSeconds || maxDurationSeconds > 180
                || maxCandidates < 1 || maxCandidates > 1_000) {
            throw new IllegalArgumentException("Analysis limits are invalid");
        }
        requireUnit(semanticWeight);
        requireUnit(audioWeight);
        requireUnit(visualWeight);
        requireUnit(narrativeWeight);
        requireUnit(hookWeight);
        requireUnit(contextPenaltyWeight);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String clean(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.substring(0, Math.min(normalized.length(), maxLength));
    }

    private static void requireUnit(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("Analysis weights must be between 0 and 1");
        }
    }
}
