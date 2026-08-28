package com.clipador.clip;

import com.clipador.clip.domain.ClipCandidate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClipCandidateRepository extends JpaRepository<ClipCandidate, UUID> {
    long countByJobId(UUID jobId);
    List<ClipCandidate> findAllByJobIdOrderByFinalScoreDesc(UUID jobId);
    List<ClipCandidate> findAllByJobIdAndSelectedTrueOrderByFinalScoreDesc(UUID jobId);
    Page<ClipCandidate> findAllByJobId(UUID jobId, Pageable pageable);
    Page<ClipCandidate> findAllByJobIdAndSelected(UUID jobId, boolean selected, Pageable pageable);
}
