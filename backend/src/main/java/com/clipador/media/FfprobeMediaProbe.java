package com.clipador.media;

import com.clipador.config.IngestionProperties;
import com.clipador.config.MediaToolsProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class FfprobeMediaProbe implements MediaProbe {
    private final ExternalProcessRunner processes;
    private final ObjectMapper objectMapper;
    private final MediaToolsProperties tools;
    private final IngestionProperties limits;

    public FfprobeMediaProbe(ExternalProcessRunner processes, ObjectMapper objectMapper,
                             MediaToolsProperties tools, IngestionProperties limits) {
        this.processes = processes;
        this.objectMapper = objectMapper;
        this.tools = tools;
        this.limits = limits;
    }

    @Override
    public MediaMetadata probe(Path mediaFile) {
        if (mediaFile == null || !Files.isRegularFile(mediaFile)) {
            throw new IllegalArgumentException("Media file does not exist");
        }
        List<String> command = List.of(
                tools.ffprobeExecutable(), "-v", "error",
                "-show_entries", "format=format_name,duration:stream=codec_type,codec_name,width,height,avg_frame_rate",
                "-of", "json", mediaFile.toAbsolutePath().toString());
        String output = processes.run(command, limits.probeTimeout(), mediaFile.getParent()).output();
        try {
            JsonNode root = objectMapper.readTree(output);
            JsonNode format = root.path("format");
            BigDecimal duration = decimal(format.path("duration"), "Media duration is unavailable");
            String container = format.path("format_name").asString("");
            JsonNode video = null;
            String audioCodec = null;
            for (JsonNode stream : root.path("streams")) {
                if ("video".equals(stream.path("codec_type").asString()) && video == null) video = stream;
                if ("audio".equals(stream.path("codec_type").asString()) && audioCodec == null) {
                    audioCodec = nullableText(stream.path("codec_name"));
                }
            }
            if (video == null) throw new IllegalArgumentException("File does not contain a video stream");
            int width = video.path("width").asInt(0);
            int height = video.path("height").asInt(0);
            BigDecimal fps = parseRate(video.path("avg_frame_rate").asString("0/1"));
            return new MediaMetadata(duration, width, height, fps,
                    nullableText(video.path("codec_name")), audioCodec, container);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("File is not a readable video", exception);
        }
    }

    private BigDecimal decimal(JsonNode node, String message) {
        String value = node.asString();
        try {
            return new BigDecimal(value).setScale(3, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private BigDecimal parseRate(String rate) {
        try {
            String[] parts = rate.split("/", 2);
            BigDecimal numerator = new BigDecimal(parts[0]);
            BigDecimal denominator = parts.length == 2 ? new BigDecimal(parts[1]) : BigDecimal.ONE;
            if (denominator.signum() == 0) return BigDecimal.ZERO.setScale(3);
            return numerator.divide(denominator, 3, RoundingMode.HALF_UP);
        } catch (RuntimeException exception) {
            return BigDecimal.ZERO.setScale(3);
        }
    }

    private String nullableText(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }
}
