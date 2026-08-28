package com.clipador.config;

import com.clipador.clip.domain.ClipFormat;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("clipador.rendering")
public record RenderingProperties(
        List<ClipFormat> formats,
        boolean burnInSubtitles,
        int videoCrf,
        String encoderPreset,
        int audioBitrateKbps,
        int outputFps,
        long maxManifestBytes,
        long maxDownloadBytes,
        boolean smartReframingEnabled,
        ReframingMode reframingMode,
        double reframingSampleFps,
        double reframingSmoothing,
        double reframingMaxPanRatioPerSecond,
        double reframingFaceMinSizeRatio,
        int reframingDetectionWidth,
        int reframingMaxKeyframes) {

    private static final Set<String> PRESETS = Set.of(
            "ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow", "slower");

    public RenderingProperties(List<ClipFormat> formats, boolean burnInSubtitles, int videoCrf,
                               String encoderPreset, int audioBitrateKbps, int outputFps,
                               long maxManifestBytes, long maxDownloadBytes) {
        this(formats, burnInSubtitles, videoCrf, encoderPreset, audioBitrateKbps, outputFps,
                maxManifestBytes, maxDownloadBytes, true, ReframingMode.AUTO, 1.5, .82,
                .35, .025, 640, 64);
    }

    @ConstructorBinding
    public RenderingProperties {
        formats = formats == null ? List.of() : List.copyOf(formats);
        if (formats.isEmpty() || formats.size() > ClipFormat.values().length
                || Set.copyOf(formats).size() != formats.size()) {
            throw new IllegalArgumentException("One to three distinct render formats are required");
        }
        if (videoCrf < 16 || videoCrf > 32 || !PRESETS.contains(encoderPreset)) {
            throw new IllegalArgumentException("H.264 CRF or preset is invalid");
        }
        if (audioBitrateKbps < 64 || audioBitrateKbps > 320 || outputFps < 24 || outputFps > 60) {
            throw new IllegalArgumentException("Audio bitrate or output FPS is invalid");
        }
        if (maxManifestBytes < 1024 || maxManifestBytes > 67_108_864L || maxDownloadBytes < 1_048_576) {
            throw new IllegalArgumentException("Rendering artifact limits are invalid");
        }
        if (reframingMode == null || reframingSampleFps < .25 || reframingSampleFps > 5
                || reframingSmoothing < 0 || reframingSmoothing > .98
                || reframingMaxPanRatioPerSecond < .05 || reframingMaxPanRatioPerSecond > 1
                || reframingFaceMinSizeRatio < .005 || reframingFaceMinSizeRatio > .25
                || reframingDetectionWidth < 160 || reframingDetectionWidth > 1280
                || reframingMaxKeyframes < 2 || reframingMaxKeyframes > 256) {
            throw new IllegalArgumentException("Smart reframing options are invalid");
        }
    }

    public int width(ClipFormat format) {
        return switch (format) {
            case VERTICAL_9_16 -> 1080;
            case LANDSCAPE_16_9 -> 1920;
            case SQUARE_1_1 -> 1080;
        };
    }

    public int height(ClipFormat format) {
        return switch (format) {
            case VERTICAL_9_16 -> 1920;
            case LANDSCAPE_16_9 -> 1080;
            case SQUARE_1_1 -> 1080;
        };
    }
}
