package com.clipador.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("clipador.ingestion")
public record IngestionProperties(
        @NotNull Duration maxVideoDuration,
        @Positive long maxUploadBytes,
        @Min(320) int maxWidth,
        @Min(240) int maxHeight,
        @NotNull Duration youtubeTimeout,
        @NotNull Duration probeTimeout) {
    public IngestionProperties {
        if (maxVideoDuration != null && (maxVideoDuration.isZero() || maxVideoDuration.isNegative())) {
            throw new IllegalArgumentException("max-video-duration must be positive");
        }
        if (youtubeTimeout != null && (youtubeTimeout.isZero() || youtubeTimeout.isNegative())) {
            throw new IllegalArgumentException("youtube-timeout must be positive");
        }
        if (probeTimeout != null && (probeTimeout.isZero() || probeTimeout.isNegative())) {
            throw new IllegalArgumentException("probe-timeout must be positive");
        }
    }
}
