package com.clipador.clip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clipador.clip.domain.ClipCandidate;
import com.clipador.clip.domain.ClipCategory;
import com.clipador.config.ClipAnalysisProperties;
import com.clipador.config.ClipQuantityProperties;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.video.domain.Video;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClipSelectionServiceTest {
    @Test
    void rejectsOverlappingAndTextuallyRedundantCandidates() {
        ClipCandidateRepository repository = mock(ClipCandidateRepository.class);
        ProcessingJob job = ProcessingJob.received(Video.upload("video.mp4", "Video"), "key", "correlation");
        UUID jobId = job.getId();
        ClipCandidate best = candidate(job, "best", 10, 55, .92,
                "Eu descobri o segredo para criar vídeos melhores com uma história completa.");
        ClipCandidate overlapping = candidate(job, "overlap", 25, 65, .88,
                "Outro texto que ocupa a mesma janela do vídeo.");
        ClipCandidate repeated = candidate(job, "repeated", 100, 145, .85,
                "Eu descobri o segredo para criar vídeos melhores com uma história completa e clara.");
        ClipCandidate diverse = candidate(job, "diverse", 180, 225, .80,
                "Uma dica prática sobre iluminação e enquadramento para entrevistas.");
        when(repository.findAllByJobIdOrderByFinalScoreDesc(jobId))
                .thenReturn(List.of(best, overlapping, repeated, diverse));
        var properties = new ClipAnalysisProperties(16_777_216, 100, 20, 45, 90,
                .30, .12, .08, .22, .23, .15, .40, .60);

        var quantity = new ClipQuantityPolicy(new ClipQuantityProperties(5, 5, 20, 1.5, 3, 30), properties);
        List<ClipCandidate> selected = new ClipSelectionService(repository, properties, quantity).select(job);

        assertThat(selected).containsExactly(best, diverse);
        assertThat(best.isSelected()).isTrue();
        assertThat(overlapping.isSelected()).isFalse();
        assertThat(repeated.isSelected()).isFalse();
    }

    private ClipCandidate candidate(ProcessingJob job, String key, double start, double end,
                                    double score, String text) {
        BigDecimal value = BigDecimal.valueOf(score);
        return ClipCandidate.analyzed(job, key, BigDecimal.valueOf(start), BigDecimal.valueOf(end),
                value, value, value, value, value, BigDecimal.valueOf(.05), value,
                "Trecho completo", "Hook", ClipCategory.INSIGHT, text, "Título envolvente");
    }
}
