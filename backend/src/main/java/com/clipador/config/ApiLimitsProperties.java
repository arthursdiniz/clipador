package com.clipador.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("clipador.api-limits")
public record ApiLimitsProperties(
        @Min(1) @Max(1000) int maxConcurrentRequests,
        @Min(1) @Max(32) int maxConcurrentUploads) {
}
