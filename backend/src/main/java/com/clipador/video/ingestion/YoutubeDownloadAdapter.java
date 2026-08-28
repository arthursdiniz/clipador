package com.clipador.video.ingestion;

import java.nio.file.Path;

public interface YoutubeDownloadAdapter {
    DownloadedYoutubeVideo download(String normalizedUrl, Path workingDirectory);
}

