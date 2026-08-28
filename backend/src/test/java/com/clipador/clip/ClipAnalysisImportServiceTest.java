package com.clipador.clip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clipador.clip.domain.ClipCandidate;
import com.clipador.config.ClipAnalysisProperties;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.storage.StorageService;
import com.clipador.video.domain.Video;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClipAnalysisImportServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void validatesAndPersistsExplainableScores() {
        Video video = Video.upload("video.mp4", "Video");
        ProcessingJob job = ProcessingJob.received(video, "key", "correlation");
        String json = """
                {"schemaVersion":1,"jobId":"%s","videoId":"%s",
                 "provider":"local-multimodal-heuristic-v1","candidates":[{
                   "candidateKey":"candidate-1","start":10.0,"end":55.0,
                   "semanticScore":0.9,"audioScore":0.7,"visualScore":0.6,
                   "narrativeScore":0.8,"hookScore":0.85,"contextPenalty":0.1,
                   "finalScore":0.82,"reason":"Ideia completa com abertura forte.",
                   "hook":"Eu descobri um segredo...","category":"INSIGHT",
                   "sourceText":"Eu descobri um segredo e expliquei toda a conclusão."}]}
                """.formatted(job.getId(), video.getId());
        StorageService storage = mock(StorageService.class);
        when(storage.open(any(), anyLong())).thenReturn(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        ClipCandidateRepository repository = mock(ClipCandidateRepository.class);
        when(repository.countByJobId(job.getId())).thenReturn(0L);
        var properties = new ClipAnalysisProperties(1024 * 1024, 100, 20, 45, 90,
                .30, .12, .08, .22, .23, .15, .40, .72);

        var result = new ClipAnalysisImportService(storage, repository, new ObjectMapper(), properties)
                .importArtifact(job, "jobs/id/analysis/candidates.json");

        assertThat(result.candidateCount()).isEqualTo(1);
        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        List<ClipCandidate> saved = (List<ClipCandidate>) captor.getValue();
        assertThat(saved.getFirst().getFinalScore()).isEqualByComparingTo("0.82000");
        assertThat(saved.getFirst().getSourceText()).contains("conclusão");
    }
}
