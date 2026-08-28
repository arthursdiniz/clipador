package com.clipador.messaging.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inbox_message")
public class InboxMessage {
    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Column(nullable = false, length = 100)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected InboxMessage() {}

    private InboxMessage(UUID messageId, String source, String payload, Instant now) {
        this.messageId = messageId;
        this.source = source;
        this.payload = payload;
        this.receivedAt = now;
        this.processedAt = now;
    }

    public static InboxMessage processed(UUID messageId, String source, String payload, Instant now) {
        return new InboxMessage(messageId, source, payload, now);
    }
}
