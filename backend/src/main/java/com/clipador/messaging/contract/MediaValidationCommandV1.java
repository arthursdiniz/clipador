package com.clipador.messaging.contract;

import java.time.Instant;
import java.util.UUID;

public record MediaValidationCommandV1(
        int schemaVersion,
        UUID messageId,
        String taskType,
        UUID jobId,
        UUID videoId,
        String correlationId,
        String storageKey,
        int attempt,
        Instant createdAt
) {
    public static final int VERSION = 1;
    public static final String TASK_TYPE = MediaTaskTypes.VALIDATE_MEDIA;

    public MediaValidationCommandV1 {
        if (schemaVersion != VERSION) throw new IllegalArgumentException("Unsupported schema version");
        if (!TASK_TYPE.equals(taskType)) throw new IllegalArgumentException("Unsupported task type");
        if (messageId == null || jobId == null || videoId == null || createdAt == null) {
            throw new IllegalArgumentException("Message identifiers and createdAt are required");
        }
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("correlationId is required");
        if (storageKey == null || storageKey.isBlank()) throw new IllegalArgumentException("storageKey is required");
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
    }
}
