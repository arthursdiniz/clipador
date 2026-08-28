package com.clipador.messaging.contract;

import java.time.Instant;
import java.util.UUID;

public record ExtractAudioCommandV1(
        int schemaVersion,
        UUID messageId,
        String taskType,
        UUID jobId,
        UUID videoId,
        String correlationId,
        String inputStorageKey,
        String outputStorageKey,
        int sampleRate,
        int channels,
        int attempt,
        Instant createdAt
) {
    public ExtractAudioCommandV1 {
        if (schemaVersion != 1 || !MediaTaskTypes.EXTRACT_AUDIO.equals(taskType)) {
            throw new IllegalArgumentException("Unsupported extract-audio contract");
        }
        if (messageId == null || jobId == null || videoId == null || createdAt == null) {
            throw new IllegalArgumentException("Message identifiers and createdAt are required");
        }
        if (inputStorageKey == null || inputStorageKey.isBlank()
                || outputStorageKey == null || outputStorageKey.isBlank()) {
            throw new IllegalArgumentException("Input and output storage keys are required");
        }
        if (correlationId == null || correlationId.isBlank() || attempt < 1) {
            throw new IllegalArgumentException("correlationId and positive attempt are required");
        }
        if (sampleRate != 16_000 || channels != 1) {
            throw new IllegalArgumentException("Phase 4 audio must be PCM mono 16 kHz");
        }
    }
}
