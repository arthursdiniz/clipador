package com.clipador.media;

import com.clipador.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class TemporaryDirectoryManager {
    private final Path root;

    public TemporaryDirectoryManager(StorageProperties properties) {
        this.root = properties.tempRoot().toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize temporary directory", exception);
        }
    }

    public Path create(UUID jobId) {
        try {
            return Files.createTempDirectory(root, "job-" + jobId + "-").toAbsolutePath().normalize();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create job temporary directory", exception);
        }
    }

    public Path stage(InputStream source, Path directory, String filename, long maxBytes) {
        Path managed = directory.toAbsolutePath().normalize();
        if (!managed.startsWith(root) || managed.equals(root) || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid staging target");
        }
        Path target = managed.resolve(filename).normalize();
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            int read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IllegalArgumentException("File exceeds the configured size limit");
                output.write(buffer, 0, read);
            }
        } catch (IOException exception) {
            deleteQuietly(target);
            throw new IllegalStateException("Could not stage uploaded video", exception);
        } catch (RuntimeException exception) {
            deleteQuietly(target);
            throw exception;
        }
        if (total == 0) {
            deleteQuietly(target);
            throw new IllegalArgumentException("File is empty");
        }
        return target;
    }

    public void cleanup(Path directory) {
        if (directory == null) return;
        Path normalized = directory.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) {
            throw new IllegalArgumentException("Refusing to clean an unmanaged directory");
        }
        if (!Files.exists(normalized)) return;
        try (var paths = Files.walk(normalized)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not enumerate temporary directory", exception);
        }
    }

    public void cleanupOrphans(Duration retention) {
        if (!Files.isDirectory(root)) return;
        Instant cutoff = Instant.now().minus(retention);
        try (var directories = Files.list(root)) {
            directories.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("job-"))
                    .filter(path -> olderThan(path, cutoff))
                    .forEach(this::cleanup);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan temporary directories", exception);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Cleanup is best-effort; a later retention sweep can retry locked files.
        }
    }

    private boolean olderThan(Path path, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
        } catch (IOException exception) {
            return false;
        }
    }
}
