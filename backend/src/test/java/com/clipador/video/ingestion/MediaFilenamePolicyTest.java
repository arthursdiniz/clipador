package com.clipador.video.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaFilenamePolicyTest {
    private final MediaFilenamePolicy policy = new MediaFilenamePolicy();

    @Test
    void stripsClientPathAndBuildsControlledStorageKey() {
        var filename = policy.validateUploadFilename("C:\\fakepath\\Interview.MP4");
        UUID videoId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertThat(filename.filename()).isEqualTo("Interview.MP4");
        assertThat(filename.extension()).isEqualTo("mp4");
        assertThat(policy.originalStorageKey(videoId, filename.extension()))
                .isEqualTo("videos/11111111-1111-1111-1111-111111111111/original.mp4");
    }

    @Test
    void rejectsUnsupportedExtensions() {
        assertThatThrownBy(() -> policy.validateUploadFilename("payload.exe"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

