package com.clipador.video.ingestion;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MediaFilenamePolicy {
    private static final Set<String> UPLOAD_EXTENSIONS = Set.of("mp4", "mov", "mkv", "webm");

    public UploadFilename validateUploadFilename(String supplied) {
        if (supplied == null || supplied.isBlank() || supplied.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Original filename is required");
        }
        String normalizedSeparators = supplied.replace('\\', '/');
        String filename = normalizedSeparators.substring(normalizedSeparators.lastIndexOf('/') + 1).trim();
        if (filename.isBlank() || filename.length() > 512) {
            throw new IllegalArgumentException("Original filename is invalid");
        }
        int separator = filename.lastIndexOf('.');
        String extension = separator < 0 ? "" : filename.substring(separator + 1).toLowerCase(Locale.ROOT);
        if (!UPLOAD_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Supported upload formats are mp4, mov, mkv and webm");
        }
        return new UploadFilename(filename, extension);
    }

    public String downloadedExtension(Path path) {
        String filename = path.getFileName().toString();
        int separator = filename.lastIndexOf('.');
        String extension = separator < 0 ? "" : filename.substring(separator + 1).toLowerCase(Locale.ROOT);
        if (!UPLOAD_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Downloaded file has an unsupported container extension");
        }
        return extension;
    }

    public String originalStorageKey(UUID videoId, String extension) {
        if (!UPLOAD_EXTENSIONS.contains(extension)) throw new IllegalArgumentException("Invalid media extension");
        return "videos/" + videoId + "/original." + extension;
    }

    public record UploadFilename(String filename, String extension) {}
}

