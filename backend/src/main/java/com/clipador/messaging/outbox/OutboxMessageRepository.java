package com.clipador.messaging.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {
    @Query(value = """
            SELECT * FROM outbox_message
             WHERE published_at IS NULL
               AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
             ORDER BY occurred_at
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxMessage> lockReadyBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
