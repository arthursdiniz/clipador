package com.clipador.clip;

import com.clipador.config.ClipAnalysisProperties;
import com.clipador.config.ClipQuantityProperties;
import com.clipador.job.domain.ClipQuantityMode;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class ClipQuantityPolicy {
    private final ClipQuantityProperties properties;

    public ClipQuantityPolicy(ClipQuantityProperties properties, ClipAnalysisProperties analysisProperties) {
        this.properties = properties;
        if (properties.maximum() > analysisProperties.maxCandidates()) {
            throw new IllegalArgumentException("Maximum clip quantity cannot exceed analysis max candidates");
        }
    }

    public void validateRequest(ClipQuantityMode mode, Integer requestedCount) {
        ClipQuantityMode effectiveMode = mode == null ? ClipQuantityMode.AUTO : mode;
        if (effectiveMode == ClipQuantityMode.MANUAL) {
            if (requestedCount == null || requestedCount < 1 || requestedCount > properties.maximum()) {
                throw new IllegalArgumentException(
                        "Manual clip count must be between 1 and " + properties.maximum());
            }
        } else if (requestedCount != null) {
            throw new IllegalArgumentException("clipCount is accepted only in MANUAL mode");
        }
    }

    public int resolve(ClipQuantityMode mode, Integer requestedCount, BigDecimal durationSeconds) {
        ClipQuantityMode effectiveMode = mode == null ? ClipQuantityMode.AUTO : mode;
        validateRequest(effectiveMode, requestedCount);
        if (effectiveMode == ClipQuantityMode.MANUAL) return requestedCount;

        int automatic = automaticCount(durationSeconds);
        if (effectiveMode == ClipQuantityMode.AUTO) return automatic;
        int multiplied = (int) Math.ceil(automatic * properties.extendedMultiplier());
        return Math.min(properties.maximum(), Math.max(automatic + properties.extendedMinExtra(), multiplied));
    }

    private int automaticCount(BigDecimal durationSeconds) {
        if (durationSeconds == null || durationSeconds.signum() <= 0) return properties.baseCount();
        double minutes = durationSeconds.doubleValue() / 60.0;
        int durationDriven = (int) Math.ceil(minutes / properties.minutesPerClip());
        return Math.min(properties.automaticMax(), Math.max(properties.baseCount(), durationDriven));
    }

    public int maximum() {
        return properties.maximum();
    }
}
