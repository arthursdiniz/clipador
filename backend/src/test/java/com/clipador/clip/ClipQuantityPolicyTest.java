package com.clipador.clip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clipador.config.ClipAnalysisProperties;
import com.clipador.config.ClipQuantityProperties;
import com.clipador.job.domain.ClipQuantityMode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ClipQuantityPolicyTest {
    private final ClipQuantityPolicy policy = new ClipQuantityPolicy(
            new ClipQuantityProperties(5, 5, 20, 1.5, 3, 30),
            new ClipAnalysisProperties(16_777_216, 100, 20, 45, 90,
                    .30, .12, .08, .22, .23, .15, .40, .72));

    @Test
    void automaticModeGrowsWithVideoDurationAndKeepsBounds() {
        assertThat(policy.resolve(ClipQuantityMode.AUTO, null, null)).isEqualTo(5);
        assertThat(policy.resolve(ClipQuantityMode.AUTO, null, seconds(10))).isEqualTo(5);
        assertThat(policy.resolve(ClipQuantityMode.AUTO, null, seconds(30))).isEqualTo(6);
        assertThat(policy.resolve(ClipQuantityMode.AUTO, null, seconds(61))).isEqualTo(13);
        assertThat(policy.resolve(ClipQuantityMode.AUTO, null, seconds(240))).isEqualTo(20);
    }

    @Test
    void extendedModeAddsAtLeastThreeAndCapsAtMaximum() {
        assertThat(policy.resolve(ClipQuantityMode.EXTENDED, null, seconds(10))).isEqualTo(8);
        assertThat(policy.resolve(ClipQuantityMode.EXTENDED, null, seconds(60))).isEqualTo(18);
        assertThat(policy.resolve(ClipQuantityMode.EXTENDED, null, seconds(240))).isEqualTo(30);
    }

    @Test
    void manualModeHonorsValidRequestAndRejectsInvalidCombinations() {
        assertThat(policy.resolve(ClipQuantityMode.MANUAL, 17, seconds(5))).isEqualTo(17);
        assertThatThrownBy(() -> policy.validateRequest(ClipQuantityMode.MANUAL, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validateRequest(ClipQuantityMode.MANUAL, 31))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validateRequest(ClipQuantityMode.AUTO, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BigDecimal seconds(int minutes) {
        return BigDecimal.valueOf(minutes * 60L);
    }
}
