package com.clipador.video;

import com.clipador.video.domain.Video;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, UUID> {}

