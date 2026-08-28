package com.clipador.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxMessageTest {
    @Test
    void appliesBoundedExponentialBackoffAndClearsErrorOnPublish() {
        Instant now = Instant.parse("2026-08-25T12:00:00Z");
        OutboxMessage message = OutboxMessage.pending(UUID.randomUUID(), UUID.randomUUID(),
                "TYPE", "route", "{}", now);

        message.failed("offline", now);
        assertThat(message.getAttemptCount()).isEqualTo(1);
        assertThat(message.getNextAttemptAt()).isEqualTo(now.plusSeconds(1));

        message.failed("still offline", now.plusSeconds(1));
        assertThat(message.getNextAttemptAt()).isEqualTo(now.plusSeconds(3));

        message.published(now.plusSeconds(4));
        assertThat(message.getPublishedAt()).isEqualTo(now.plusSeconds(4));
        assertThat(message.getNextAttemptAt()).isNull();
        assertThat(message.getLastError()).isNull();
    }
}
