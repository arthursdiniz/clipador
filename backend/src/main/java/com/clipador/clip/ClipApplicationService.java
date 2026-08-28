package com.clipador.clip;

import com.clipador.clip.domain.Clip;
import com.clipador.shared.api.ResourceNotFoundException;
import com.clipador.shared.api.ConflictException;
import com.clipador.storage.StorageService;
import com.clipador.config.RenderingProperties;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.Locale;
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
        String filename = downloadFilename(clip);
        return new ClipDownload(input, filename);
    }

    private String downloadFilename(Clip clip) {
        String title = clip.getCandidate().getTitle();
        if (title == null || title.isBlank()) return "clip-" + clip.getId() + ".mp4";
        String ascii = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (ascii.length() > 100) ascii = ascii.substring(0, 100).replaceAll("-+$", "");
        return (ascii.isBlank() ? "clip-" + clip.getId() : ascii) + ".mp4";
    }

    public record ClipDownload(InputStream input, String filename) {}
}
