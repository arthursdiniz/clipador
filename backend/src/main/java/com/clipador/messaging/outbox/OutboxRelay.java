package com.clipador.messaging.outbox;

import com.clipador.messaging.MessagingProperties;
import com.clipador.messaging.RabbitPublisher;
import com.clipador.messaging.RabbitTopology;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxMessageRepository messages;
    private final RabbitPublisher publisher;
    private final MessagingProperties properties;
    private final Counter published;
    private final Counter failures;
    private final Clock clock = Clock.systemUTC();

    public OutboxRelay(OutboxMessageRepository messages, RabbitPublisher publisher,
                       MessagingProperties properties, MeterRegistry registry) {
        this.messages = messages;
        this.publisher = publisher;
        this.properties = properties;
        this.published = Counter.builder("clipador.outbox.published").register(registry);
        this.failures = Counter.builder("clipador.outbox.failures").register(registry);
    }

    @Scheduled(fixedDelayString = "${clipador.messaging.outbox-interval:PT1S}")
    @Transactional
    public void publishReady() {
        Instant now = clock.instant();
        for (OutboxMessage message : messages.lockReadyBatch(now, properties.outboxBatchSize())) {
            try {
                publisher.publish(RabbitTopology.COMMAND_EXCHANGE, message.getRoutingKey(), message.getId(),
                        message.getMessageType(), message.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        Map.of("x-schema-version", 1));
                message.published(clock.instant());
                published.increment();
            } catch (RuntimeException exception) {
                message.failed(exception.getMessage(), clock.instant());
                failures.increment();
                log.warn("Outbox publish failed messageId={} aggregateId={} attempt={}",
                        message.getId(), message.getAggregateId(), message.getAttemptCount(), exception);
            }
        }
    }
}
