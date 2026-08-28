package com.clipador.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("clipador.messaging")
public record MessagingProperties(
        Duration publishTimeout,
        Duration outboxInterval,
        int outboxBatchSize,
        Duration retryDelay,
        int resultMaxRetries
) {
    public MessagingProperties {
        if (publishTimeout == null || publishTimeout.isNegative() || publishTimeout.isZero()) {
            throw new IllegalArgumentException("publishTimeout must be positive");
        }
        if (outboxInterval == null || outboxInterval.isNegative() || outboxInterval.isZero()) {
            throw new IllegalArgumentException("outboxInterval must be positive");
        }
        if (outboxBatchSize < 1 || outboxBatchSize > 500) {
            throw new IllegalArgumentException("outboxBatchSize must be between 1 and 500");
        }
        if (retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()) {
            throw new IllegalArgumentException("retryDelay must be positive");
        }
        if (resultMaxRetries < 0 || resultMaxRetries > 20) {
            throw new IllegalArgumentException("resultMaxRetries must be between 0 and 20");
        }
    }
}
