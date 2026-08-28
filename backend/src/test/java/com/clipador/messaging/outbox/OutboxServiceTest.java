package com.clipador.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clipador.job.domain.JobStatus;
import com.clipador.config.ClipAnalysisProperties;
import com.clipador.config.RenderingProperties;
import com.clipador.clip.domain.ClipFormat;
import java.util.List;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.video.domain.Video;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxServiceTest {
    @Test
    void buildsVersionedCommandFromDownloadedJob() throws Exception {
        OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper mapper = new ObjectMapper();
        OutboxService service = new OutboxService(repository, mapper, analysisProperties(), renderingProperties());
        Video video = Video.upload("video.mp4", "Video");
        video.completeIngestion("videos/" + video.getId() + "/original.mp4", BigDecimal.TEN,
                1920, 1080, BigDecimal.valueOf(30), "h264", "aac",
                null, null, null, null, null);
        ProcessingJob job = ProcessingJob.received(video, "key", "correlation");
        job.transitionTo(JobStatus.DOWNLOADING, 5, Instant.now());
        job.transitionTo(JobStatus.DOWNLOADED, 15, Instant.now());

        service.enqueueMediaValidation(job, video, video.getStoragePath());

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(repository).save(captor.capture());
        var json = mapper.readTree(captor.getValue().getPayload());
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("jobId").asText()).isEqualTo(job.getId().toString());
        assertThat(json.get("storageKey").asText()).isEqualTo(video.getStoragePath());
    }

    private ClipAnalysisProperties analysisProperties() {
        return new ClipAnalysisProperties(16_777_216, 100, 20, 45, 90,
                .30, .12, .08, .22, .23, .15, .40, .72);
    }

    private RenderingProperties renderingProperties() {
        return new RenderingProperties(List.of(ClipFormat.VERTICAL_9_16), true, 21,
                "medium", 160, 30, 16_777_216, 2_147_483_648L);
    }
}
