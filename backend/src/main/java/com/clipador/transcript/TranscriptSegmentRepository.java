package com.clipador.transcript;

import com.clipador.transcript.domain.TranscriptSegment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, UUID> {
    Page<TranscriptSegment> findAllByTranscriptId(UUID transcriptId, Pageable pageable);
    long countByTranscriptId(UUID transcriptId);
}
