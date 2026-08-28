package com.clipador.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("clipador.cleanup")
public record CleanupProperties(
        @NotNull Duration orphanRetention,
        @NotNull Duration interval,
        @NotNull Duration initialDelay) {
    public CleanupProperties {
        if (orphanRetention != null && (orphanRetention.isZero() || orphanRetention.isNegative())) {
            throw new IllegalArgumentException("orphan-retention must be positive");
        }
        if (interval != null && (interval.isZero() || interval.isNegative())) {
            throw new IllegalArgumentException("cleanup interval must be positive");
        }
        if (initialDelay != null && initialDelay.isNegative()) {
            throw new IllegalArgumentException("cleanup initial delay must not be negative");
        }
    }
}
