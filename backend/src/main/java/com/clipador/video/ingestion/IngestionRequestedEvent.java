package com.clipador.video.ingestion;

import java.util.UUID;

public record IngestionRequestedEvent(
        UUID jobId,
        UUID videoId,
        String correlationId,
        String normalizedSourceUrl) {}

