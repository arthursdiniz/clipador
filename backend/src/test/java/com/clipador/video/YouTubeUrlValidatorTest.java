package com.clipador.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class YouTubeUrlValidatorTest {
    private final YouTubeUrlValidator validator = new YouTubeUrlValidator();

    @Test
    void normalizesSupportedYoutubeUrls() {
        assertThat(validator.normalize("https://youtu.be/dQw4w9WgXcQ?t=12"))
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(validator.normalize("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }

    @Test
    void rejectsNonHttpsAndLookalikeHosts() {
        assertThatThrownBy(() -> validator.normalize("http://youtube.com/watch?v=dQw4w9WgXcQ"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.normalize("https://youtube.com.attacker.test/watch?v=dQw4w9WgXcQ"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.normalize("https://127.0.0.1/watch?v=dQw4w9WgXcQ"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

