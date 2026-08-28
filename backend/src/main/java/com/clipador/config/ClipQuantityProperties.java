package com.clipador.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("clipador.clip-quantity")
public record ClipQuantityProperties(
        int baseCount,
        double minutesPerClip,
        int automaticMax,
        double extendedMultiplier,
        int extendedMinExtra,
        int maximum) {

    public ClipQuantityProperties {
        if (baseCount < 1 || baseCount > 100
                || !Double.isFinite(minutesPerClip) || minutesPerClip <= 0
                || automaticMax < baseCount || automaticMax > 100
                || !Double.isFinite(extendedMultiplier) || extendedMultiplier <= 1
                || extendedMinExtra < 1
                || maximum < automaticMax || maximum > 100) {
            throw new IllegalArgumentException("Clip quantity configuration is invalid");
        }
    }
}
