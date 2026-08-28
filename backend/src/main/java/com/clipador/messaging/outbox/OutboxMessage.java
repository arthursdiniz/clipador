package com.clipador.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_message")
public class OutboxMessage {
    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "message_type", nullable = false, length = 100)
    private String messageType;

    @Column(name = "routing_key", nullable = false, length = 200)
    private String routingKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    protected OutboxMessage() {}

    private OutboxMessage(UUID id, UUID aggregateId, String messageType, String routingKey,
                          String payload, Instant occurredAt) {
        this.id = id;
        this.aggregateType = "ProcessingJob";
        this.aggregateId = aggregateId;
        this.messageType = messageType;
        this.routingKey = routingKey;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.nextAttemptAt = occurredAt;
    }

    public static OutboxMessage pending(UUID id, UUID aggregateId, String messageType,
                                        String routingKey, String payload, Instant occurredAt) {
        return new OutboxMessage(id, aggregateId, messageType, routingKey, payload, occurredAt);
    }

    public void published(Instant now) {
        publishedAt = now;
        lastError = null;
        nextAttemptAt = null;
    }

    public void failed(String error, Instant now) {
        attemptCount++;
        long backoffSeconds = Math.min(300, 1L << Math.min(attemptCount - 1, 8));
        nextAttemptAt = now.plus(Duration.ofSeconds(backoffSeconds));
        lastError = truncate(error, 4000);
    }

    private String truncate(String value, int limit) {
        if (value == null || value.isBlank()) return "RabbitMQ publish failed";
        return value.substring(0, Math.min(value.length(), limit));
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getMessageType() { return messageType; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastError() { return lastError; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
}
