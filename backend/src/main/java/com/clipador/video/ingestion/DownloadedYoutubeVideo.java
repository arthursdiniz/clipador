package com.clipador.video.ingestion;

import java.nio.file.Path;

public record DownloadedYoutubeVideo(Path path, YoutubeSourceMetadata sourceMetadata) {}

