package com.clipador.messaging.contract;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MediaTaskResultV1(
        int schemaVersion,
        UUID messageId,
        UUID commandMessageId,
        String taskType,
        UUID jobId,
        UUID videoId,
        String correlationId,
        Status status,
        String errorCode,
        String errorMessage,
        Map<String, Object> details,
        Instant completedAt
) {
    public enum Status { SUCCEEDED, FAILED }

    public MediaTaskResultV1 {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported schema version");
        if (!MediaTaskTypes.isSupported(taskType)) throw new IllegalArgumentException("Unsupported task type");
        if (messageId == null || commandMessageId == null || jobId == null || videoId == null || completedAt == null) {
            throw new IllegalArgumentException("Message identifiers and completedAt are required");
        }
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("correlationId is required");
        if (status == null) throw new IllegalArgumentException("status is required");
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
