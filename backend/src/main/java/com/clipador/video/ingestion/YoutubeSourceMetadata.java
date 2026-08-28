package com.clipador.video.ingestion;

import java.math.BigDecimal;

public record YoutubeSourceMetadata(
        String title,
        String channel,
        BigDecimal durationSeconds,
        String thumbnailUrl,
        String description,
        String language) {}

