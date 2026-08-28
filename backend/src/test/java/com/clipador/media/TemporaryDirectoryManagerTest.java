package com.clipador.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clipador.config.StorageProperties;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemporaryDirectoryManagerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void stagesBoundedUploadAndCleansManagedDirectory() throws Exception {
        TemporaryDirectoryManager manager = manager();
        Path jobDirectory = manager.create(UUID.randomUUID());

        Path staged = manager.stage(new ByteArrayInputStream(new byte[] {1, 2, 3}),
                jobDirectory, "upload.mp4", 10);
        assertThat(Files.size(staged)).isEqualTo(3);

        manager.cleanup(jobDirectory);
        assertThat(jobDirectory).doesNotExist();
    }

    @Test
    void removesPartialFileWhenLimitIsExceeded() throws Exception {
        TemporaryDirectoryManager manager = manager();
        Path jobDirectory = manager.create(UUID.randomUUID());

        assertThatThrownBy(() -> manager.stage(new ByteArrayInputStream(new byte[20]),
                jobDirectory, "upload.mp4", 10))
                .isInstanceOf(IllegalArgumentException.class);
        try (var files = Files.list(jobDirectory)) {
            assertThat(files).isEmpty();
        }
    }

    private TemporaryDirectoryManager manager() {
        TemporaryDirectoryManager manager = new TemporaryDirectoryManager(new StorageProperties("local",
                temporaryDirectory.resolve("storage"), temporaryDirectory.resolve("tmp")));
        manager.initialize();
        return manager;
    }
}
