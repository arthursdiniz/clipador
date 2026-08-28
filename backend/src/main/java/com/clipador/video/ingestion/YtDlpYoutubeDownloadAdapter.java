package com.clipador.video.ingestion;

import com.clipador.config.IngestionProperties;
import com.clipador.config.MediaToolsProperties;
import com.clipador.media.ExternalProcessRunner;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class YtDlpYoutubeDownloadAdapter implements YoutubeDownloadAdapter {
    private final ExternalProcessRunner processes;
    private final ObjectMapper objectMapper;
    private final MediaToolsProperties tools;
    private final IngestionProperties limits;

    public YtDlpYoutubeDownloadAdapter(ExternalProcessRunner processes, ObjectMapper objectMapper,
                                       MediaToolsProperties tools, IngestionProperties limits) {
        this.processes = processes;
        this.objectMapper = objectMapper;
        this.tools = tools;
        this.limits = limits;
    }

    @Override
    public DownloadedYoutubeVideo download(String normalizedUrl, Path workingDirectory) {
        YoutubeSourceMetadata metadata = inspect(normalizedUrl, workingDirectory);
        if (metadata.durationSeconds() != null
                && metadata.durationSeconds().compareTo(maxDurationSeconds()) > 0) {
            throw new IllegalArgumentException("YouTube video exceeds the configured duration limit");
        }

        String output = processes.run(buildDownloadCommand(normalizedUrl, workingDirectory),
                limits.youtubeTimeout(), workingDirectory).output();
        Path downloaded = lastOutputPath(output, workingDirectory);
        try {
            if (!Files.isRegularFile(downloaded)) throw new IllegalArgumentException("yt-dlp did not produce a video file");
            if (Files.size(downloaded) > limits.maxUploadBytes()) {
                throw new IllegalArgumentException("Downloaded video exceeds the configured size limit");
            }
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Could not inspect downloaded video", exception);
        }
        return new DownloadedYoutubeVideo(downloaded, metadata);
    }

    List<String> buildMetadataCommand(String normalizedUrl) {
        return List.of(tools.ytDlpExecutable(), "--ignore-config", "--no-playlist", "--no-warnings",
                "--ies", "youtube", "--simulate", "--dump-single-json", "--", normalizedUrl);
    }

    List<String> buildDownloadCommand(String normalizedUrl, Path workingDirectory) {
        List<String> command = new ArrayList<>();
        command.addAll(List.of(tools.ytDlpExecutable(), "--ignore-config", "--no-playlist", "--no-warnings",
                "--quiet", "--ies", "youtube", "--socket-timeout", "15", "--retries", "3",
                "--fragment-retries", "3", "--extractor-retries", "3",
                "--match-filters", "duration <= " + maxDurationSeconds().toPlainString(),
                "--max-filesize", Long.toString(limits.maxUploadBytes()),
                "--format", "bv*+ba/b", "--merge-output-format", "mp4", "--remux-video", "mp4",
                "--output", workingDirectory.resolve("source.%(ext)s").toAbsolutePath().toString(),
                "--print", "after_move:filepath", "--", normalizedUrl));
        return List.copyOf(command);
    }

    private YoutubeSourceMetadata inspect(String normalizedUrl, Path workingDirectory) {
        String output = processes.run(buildMetadataCommand(normalizedUrl),
                limits.probeTimeout(), workingDirectory).output().trim();
        try {
            JsonNode root = objectMapper.readTree(output);
            return new YoutubeSourceMetadata(text(root, "title"), firstText(root, "channel", "uploader"),
                    decimal(root, "duration"), text(root, "thumbnail"), text(root, "description"),
                    text(root, "language"));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not parse YouTube metadata", exception);
        }
    }

    private Path lastOutputPath(String output, Path workingDirectory) {
        String[] lines = output.strip().split("\\R");
        if (lines.length == 0 || lines[lines.length - 1].isBlank()) {
            throw new IllegalArgumentException("yt-dlp did not report the downloaded path");
        }
        Path candidate = Path.of(lines[lines.length - 1].trim());
        if (!candidate.isAbsolute()) candidate = workingDirectory.resolve(candidate);
        candidate = candidate.toAbsolutePath().normalize();
        Path allowedRoot = workingDirectory.toAbsolutePath().normalize();
        if (!candidate.startsWith(allowedRoot)) {
            throw new IllegalArgumentException("yt-dlp produced a path outside its working directory");
        }
        return candidate;
    }

    private BigDecimal maxDurationSeconds() {
        return BigDecimal.valueOf(limits.maxVideoDuration().toMillis(), 3);
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private String firstText(JsonNode root, String... fields) {
        for (String field : fields) {
            String value = text(root, field);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private BigDecimal decimal(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        try {
            return new BigDecimal(value.asString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
