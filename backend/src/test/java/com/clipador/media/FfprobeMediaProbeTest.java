package com.clipador.media;

import static org.assertj.core.api.Assertions.assertThat;
import com.clipador.config.IngestionProperties;
import com.clipador.config.MediaToolsProperties;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class FfprobeMediaProbeTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesTechnicalMetadataFromFfprobeJson() throws Exception {
        Path media = Files.write(temporaryDirectory.resolve("video.bin"), new byte[] {1});
        String json = """
                {"streams":[
                  {"codec_type":"video","codec_name":"h264","width":1920,"height":1080,"avg_frame_rate":"30000/1001"},
                  {"codec_type":"audio","codec_name":"aac"}
                ],"format":{"format_name":"mov,mp4,m4a,3gp,3g2,mj2","duration":"61.2345"}}
                """;
        ExternalProcessRunner runner = new ExternalProcessRunner() {
            @Override
            public ProcessResult run(java.util.List<String> command, Duration timeout, Path workingDirectory) {
                return new ProcessResult(0, json);
            }
        };
        FfprobeMediaProbe probe = new FfprobeMediaProbe(runner, new ObjectMapper(),
                new MediaToolsProperties("yt-dlp", "ffprobe"), properties());

        MediaMetadata metadata = probe.probe(media);

        assertThat(metadata.durationSeconds()).isEqualByComparingTo("61.235");
        assertThat(metadata.fps()).isEqualByComparingTo(new BigDecimal("29.970"));
        assertThat(metadata.videoCodec()).isEqualTo("h264");
        assertThat(metadata.audioCodec()).isEqualTo("aac");
    }

    private IngestionProperties properties() {
        return new IngestionProperties(Duration.ofHours(4), 5_000_000, 3840, 2160,
                Duration.ofMinutes(30), Duration.ofSeconds(30));
    }
}
