package com.clipador.media;

import com.clipador.config.IngestionProperties;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MediaValidator {
    private static final Set<String> SUPPORTED_CONTAINERS = Set.of(
            "mov", "mp4", "m4a", "3gp", "3g2", "mj2", "matroska", "webm");

    private final IngestionProperties limits;

    public MediaValidator(IngestionProperties limits) {
        this.limits = limits;
    }

    public void validate(MediaMetadata metadata) {
        boolean supported = Arrays.stream(metadata.container().split(","))
                .map(String::trim)
                .anyMatch(SUPPORTED_CONTAINERS::contains);
        if (!supported) throw new IllegalArgumentException("Unsupported video container");
        if (metadata.durationSeconds().signum() <= 0) throw new IllegalArgumentException("Video duration must be positive");
        BigDecimal maxDuration = BigDecimal.valueOf(limits.maxVideoDuration().toMillis(), 3);
        if (metadata.durationSeconds().compareTo(maxDuration) > 0) {
            throw new IllegalArgumentException("Video exceeds the configured duration limit");
        }
        boolean landscapeWithinLimit = metadata.width() <= limits.maxWidth() && metadata.height() <= limits.maxHeight();
        boolean portraitWithinLimit = metadata.width() <= limits.maxHeight() && metadata.height() <= limits.maxWidth();
        if (metadata.width() <= 0 || metadata.height() <= 0
                || (!landscapeWithinLimit && !portraitWithinLimit)) {
            throw new IllegalArgumentException("Video exceeds the configured resolution limit");
        }
    }
}
