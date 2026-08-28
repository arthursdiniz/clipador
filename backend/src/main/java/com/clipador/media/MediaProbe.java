package com.clipador.media;

import java.nio.file.Path;

public interface MediaProbe {
    MediaMetadata probe(Path mediaFile);
}

