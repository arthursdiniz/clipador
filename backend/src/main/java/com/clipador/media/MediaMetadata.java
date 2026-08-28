package com.clipador.media;

import java.math.BigDecimal;

public record MediaMetadata(
        BigDecimal durationSeconds,
        int width,
        int height,
        BigDecimal fps,
        String videoCodec,
        String audioCodec,
        String container) {}

