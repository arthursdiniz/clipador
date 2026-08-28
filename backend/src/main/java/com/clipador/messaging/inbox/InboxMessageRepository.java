package com.clipador.messaging.inbox;

import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO inbox_message(message_id, source, payload, received_at, processed_at)
            VALUES (:messageId, :source, CAST(:payload AS jsonb), :now, :now)
            ON CONFLICT (message_id) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("messageId") UUID messageId, @Param("source") String source,
              @Param("payload") String payload, @Param("now") Instant now);
}
