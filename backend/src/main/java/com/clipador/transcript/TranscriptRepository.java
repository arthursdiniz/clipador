package com.clipador.transcript;

import com.clipador.transcript.domain.Transcript;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptRepository extends JpaRepository<Transcript, UUID> {
    Optional<Transcript> findByJobId(UUID jobId);
}
