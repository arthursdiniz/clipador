package com.clipador.clip;

import com.clipador.clip.domain.Clip;
import java.util.UUID;
import java.util.Optional;
import com.clipador.clip.domain.ClipFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClipRepository extends JpaRepository<Clip, UUID> {
    @Override
    @EntityGraph(attributePaths = "candidate")
    Optional<Clip> findById(UUID id);

    @EntityGraph(attributePaths = "candidate")
    Page<Clip> findAllByJobVideoId(UUID videoId, Pageable pageable);

    Optional<Clip> findByCandidateIdAndFormat(UUID candidateId, ClipFormat format);
}
