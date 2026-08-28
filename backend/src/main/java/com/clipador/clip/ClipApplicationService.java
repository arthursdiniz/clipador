package com.clipador.clip;

import com.clipador.clip.domain.Clip;
import com.clipador.shared.api.ResourceNotFoundException;
import com.clipador.shared.api.ConflictException;
import com.clipador.storage.StorageService;
import com.clipador.config.RenderingProperties;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClipApplicationService {
    private final ClipRepository clips;
    private final StorageService storage;
    private final RenderingProperties renderingProperties;

    public ClipApplicationService(ClipRepository clips, StorageService storage,
                                  RenderingProperties renderingProperties) {
        this.clips = clips;
        this.storage = storage;
        this.renderingProperties = renderingProperties;
    }

    @Transactional(readOnly = true)
    public Clip findById(UUID id) {
        return clips.findById(id).orElseThrow(() -> new ResourceNotFoundException("Clip", id));
    }

    @Transactional(readOnly = true)
    public Page<Clip> findByVideo(UUID videoId, Pageable pageable) {
        return clips.findAllByJobVideoId(videoId, pageable);
    }

    @Transactional(readOnly = true)
    public ClipDownload download(UUID id) {
        Clip clip = findById(id);
        if (clip.getStoragePath() == null) {
            throw new ConflictException("Clip rendering failed and has no downloadable artifact");
        }
        InputStream input = storage.open(clip.getStoragePath(), renderingProperties.maxDownloadBytes());
        String filename = "clip-" + clip.getId() + "-" + clip.getFormat().name().toLowerCase() + ".mp4";
        return new ClipDownload(input, filename);
    }

    public record ClipDownload(InputStream input, String filename) {}
}
