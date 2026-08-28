package com.clipador.media;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clipador.config.IngestionProperties;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MediaValidatorTest {
    private final MediaValidator validator = new MediaValidator(new IngestionProperties(
            Duration.ofHours(4), 5_000_000, 3840, 2160, Duration.ofMinutes(30), Duration.ofSeconds(30)));

    @Test
    void acceptsSupportedVideoWithinLimits() {
        assertThatCode(() -> validator.validate(new MediaMetadata(
                new BigDecimal("120.500"), 1920, 1080, new BigDecimal("29.970"),
                "h264", "aac", "mov,mp4,m4a,3gp,3g2,mj2")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsFakeContainerAndExcessiveResolution() {
        assertThatThrownBy(() -> validator.validate(new MediaMetadata(
                BigDecimal.TEN, 1920, 1080, BigDecimal.valueOf(30), "h264", "aac", "image2")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("container");
        assertThatThrownBy(() -> validator.validate(new MediaMetadata(
                BigDecimal.TEN, 7680, 4320, BigDecimal.valueOf(30), "h264", "aac", "mp4")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("resolution");
    }
}

