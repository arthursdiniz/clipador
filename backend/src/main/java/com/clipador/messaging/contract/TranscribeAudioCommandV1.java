package com.clipador.messaging.contract;

import java.time.Instant;
import java.util.UUID;

public record TranscribeAudioCommandV1(
        int schemaVersion,
        UUID messageId,
        String taskType,
        UUID jobId,
        UUID videoId,
        String correlationId,
        String audioStorageKey,
        String transcriptStorageKey,
        String language,
        boolean wordTimestamps,
        boolean vadEnabled,
        int attempt,
        Instant createdAt
) {
    public TranscribeAudioCommandV1 {
        if (schemaVersion != 1 || !MediaTaskTypes.TRANSCRIBE_AUDIO.equals(taskType)) {
            throw new IllegalArgumentException("Unsupported transcription contract");
        }
        if (messageId == null || jobId == null || videoId == null || createdAt == null) {
            throw new IllegalArgumentException("Message identifiers and createdAt are required");
        }
        if (audioStorageKey == null || audioStorageKey.isBlank()
                || transcriptStorageKey == null || transcriptStorageKey.isBlank()) {
            throw new IllegalArgumentException("Audio and transcript storage keys are required");
        }
        if (correlationId == null || correlationId.isBlank() || attempt < 1) {
            throw new IllegalArgumentException("correlationId and positive attempt are required");
        }
        if (!wordTimestamps || !vadEnabled) {
            throw new IllegalArgumentException("Phase 4 requires word timestamps and VAD");
        }
    }
}
