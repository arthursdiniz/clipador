package com.clipador.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("clipador.transcription")
public record TranscriptionProperties(long maxArtifactBytes, int maxSegments, int persistenceBatchSize) {
    public TranscriptionProperties {
        if (maxArtifactBytes < 1024 || maxArtifactBytes > 268_435_456L) {
            throw new IllegalArgumentException("maxArtifactBytes must be between 1 KiB and 256 MiB");
        }
        if (maxSegments < 1 || maxSegments > 200_000) {
            throw new IllegalArgumentException("maxSegments must be between 1 and 200000");
        }
        if (persistenceBatchSize < 1 || persistenceBatchSize > 2_000) {
            throw new IllegalArgumentException("persistenceBatchSize must be between 1 and 2000");
        }
    }
}
