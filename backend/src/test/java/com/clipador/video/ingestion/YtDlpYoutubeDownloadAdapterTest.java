package com.clipador.video.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.clipador.config.IngestionProperties;
import com.clipador.config.MediaToolsProperties;
import com.clipador.media.ExternalProcessRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class YtDlpYoutubeDownloadAdapterTest {

    @Test
    void buildsFixedArgumentListWithoutShellOrUserOptions() {
        var adapter = new YtDlpYoutubeDownloadAdapter(new ExternalProcessRunner(), new ObjectMapper(),
                new MediaToolsProperties("yt-dlp", "ffprobe"), properties());
        String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

        List<String> command = adapter.buildDownloadCommand(url, Path.of("work").toAbsolutePath());

        assertThat(command.getFirst()).isEqualTo("yt-dlp");
        assertThat(command).contains("--ignore-config", "--no-playlist", "--max-filesize", "5000000", "--", url);
        assertThat(command).doesNotContain("sh", "cmd", "powershell", "-c");
        assertThat(command.getLast()).isEqualTo(url);
    }

    @Test
    void requestsNativeJsonMetadataWithoutAnInlineTemplate() {
        var adapter = new YtDlpYoutubeDownloadAdapter(new ExternalProcessRunner(), new ObjectMapper(),
                new MediaToolsProperties("yt-dlp", "ffprobe"), properties());
        String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

        List<String> command = adapter.buildMetadataCommand(url);

        assertThat(command).contains("--simulate", "--dump-single-json", "--", url);
        assertThat(command).doesNotContain("--print");
        assertThat(command).noneMatch(argument -> argument.contains("%(title)"));
    }

    private IngestionProperties properties() {
        return new IngestionProperties(Duration.ofHours(4), 5_000_000, 3840, 2160,
                Duration.ofMinutes(30), Duration.ofSeconds(30));
    }
}
