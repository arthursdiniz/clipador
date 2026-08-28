package com.clipador.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("clipador.analysis")
public record ClipAnalysisProperties(
        long maxArtifactBytes,
        int maxCandidates,
        double minDurationSeconds,
        double idealDurationSeconds,
        double maxDurationSeconds,
        double semanticWeight,
        double audioWeight,
        double visualWeight,
        double narrativeWeight,
        double hookWeight,
        double contextPenaltyWeight,
        double overlapThreshold,
        double similarityThreshold) {

    public ClipAnalysisProperties {
        if (maxArtifactBytes < 1024 || maxArtifactBytes > 134_217_728L) {
            throw new IllegalArgumentException("maxArtifactBytes must be between 1 KiB and 128 MiB");
        }
        if (maxCandidates < 1 || maxCandidates > 1_000) {
            throw new IllegalArgumentException("Candidate count is invalid");
        }
        if (minDurationSeconds < 5 || idealDurationSeconds < minDurationSeconds
                || maxDurationSeconds < idealDurationSeconds || maxDurationSeconds > 180) {
            throw new IllegalArgumentException("Clip duration limits are invalid");
        }
        requireUnit(semanticWeight, "semanticWeight");
        requireUnit(audioWeight, "audioWeight");
        requireUnit(visualWeight, "visualWeight");
        requireUnit(narrativeWeight, "narrativeWeight");
        requireUnit(hookWeight, "hookWeight");
        requireUnit(contextPenaltyWeight, "contextPenaltyWeight");
        requireUnit(overlapThreshold, "overlapThreshold");
        requireUnit(similarityThreshold, "similarityThreshold");
        if (semanticWeight + audioWeight + visualWeight + narrativeWeight + hookWeight <= 0) {
            throw new IllegalArgumentException("At least one positive scoring weight is required");
        }
    }

    private static void requireUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
