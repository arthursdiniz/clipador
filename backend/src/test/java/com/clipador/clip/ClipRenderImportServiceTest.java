package com.clipador.clip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clipador.clip.domain.Clip;
import com.clipador.clip.domain.ClipCandidate;
import com.clipador.clip.domain.ClipCategory;
import com.clipador.clip.domain.ClipFormat;
import com.clipador.config.RenderingProperties;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.storage.StorageService;
import com.clipador.video.domain.Video;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClipRenderImportServiceTest {
    @Test
    void persistsSuccessfulRenderAndAllSubtitlePaths() {
        Video video = Video.upload("video.mp4", "Video");
        ProcessingJob job = ProcessingJob.received(video, "key", "correlation");
        ClipCandidate candidate = ClipCandidate.analyzed(job, "candidate", BigDecimal.TEN,
                BigDecimal.valueOf(55), score(.9), score(.7), score(.6), score(.8), score(.9),
                score(.1), score(.85), "Complete", "Hook", ClipCategory.INSIGHT, "Source text",
                "O segredo que mudou tudo");
        candidate.select();
        String prefix = "jobs/%s/clips/%s/vertical_9_16".formatted(job.getId(), candidate.getId());
        String manifest = """
                {"schemaVersion":1,"jobId":"%s","videoId":"%s","renders":[{
                  "candidateId":"%s","format":"VERTICAL_9_16","status":"SUCCEEDED",
                  "width":1080,"height":1920,"durationSeconds":45.0,
                  "storageKey":"%s/clip.mp4","srtStorageKey":"%s/subtitles.srt",
                  "vttStorageKey":"%s/subtitles.vtt","assStorageKey":"%s/subtitles.ass",
                  "thumbnailStorageKey":"%s/thumbnail.jpg","errorCode":null,"errorMessage":null}]}
                """.formatted(job.getId(), video.getId(), candidate.getId(),
                prefix, prefix, prefix, prefix, prefix);
        StorageService storage = mock(StorageService.class);
        when(storage.open(any(), anyLong())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            byte[] bytes = key.endsWith("manifest.json") ? manifest.getBytes(StandardCharsets.UTF_8)
                    : "rendered-video".getBytes(StandardCharsets.UTF_8);
            return new ByteArrayInputStream(bytes);
        });
        ClipRepository clips = mock(ClipRepository.class);
        when(clips.findByCandidateIdAndFormat(candidate.getId(), ClipFormat.VERTICAL_9_16))
                .thenReturn(Optional.empty());
        ClipCandidateRepository candidates = mock(ClipCandidateRepository.class);
        when(candidates.findAllByJobIdAndSelectedTrueOrderByFinalScoreDesc(job.getId()))
                .thenReturn(List.of(candidate));
        RenderingProperties properties = new RenderingProperties(List.of(ClipFormat.VERTICAL_9_16),
                true, 21, "medium", 160, 30, 1024 * 1024, 1024 * 1024);
        ClipRenderImportService service = new ClipRenderImportService(storage, clips, candidates,
                new ObjectMapper(), properties);

        var result = service.importManifest(job, "jobs/" + job.getId() + "/render/manifest.json");

        assertThat(result.succeeded()).isEqualTo(1);
        ArgumentCaptor<Clip> captor = ArgumentCaptor.forClass(Clip.class);
        verify(clips).save(captor.capture());
        assertThat(captor.getValue().getSrtPath()).endsWith("subtitles.srt");
        assertThat(captor.getValue().getAssPath()).endsWith("subtitles.ass");
    }

    private BigDecimal score(double value) {
        return BigDecimal.valueOf(value);
    }
}
