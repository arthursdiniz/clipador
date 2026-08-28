package com.clipador.job;

import com.clipador.job.domain.ProcessingJob;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {
    @EntityGraph(attributePaths = "video")
    Optional<ProcessingJob> findByIdempotencyKey(String idempotencyKey);

    @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) locked", nativeQuery = true)
    int acquireIdempotencyLock(@Param("key") String key);
    Page<ProcessingJob> findAllByVideoId(UUID videoId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ProcessingJob j where j.id = :id")
    Optional<ProcessingJob> findByIdForUpdate(@Param("id") UUID id);
}
