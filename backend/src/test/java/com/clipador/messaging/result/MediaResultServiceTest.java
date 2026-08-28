package com.clipador.messaging.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clipador.event.ProcessingEventRepository;
import com.clipador.job.ProcessingJobRepository;
import com.clipador.job.domain.JobStatus;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.messaging.contract.MediaTaskResultV1;
import com.clipador.messaging.inbox.InboxMessageRepository;
import com.clipador.messaging.outbox.OutboxService;
import com.clipador.observability.PipelineMetrics;
import com.clipador.transcript.TranscriptImportService;
import com.clipador.clip.ClipAnalysisImportService;
import com.clipador.clip.ClipSelectionService;
import com.clipador.clip.ClipRenderImportService;
import com.clipador.video.domain.Video;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaResultServiceTest {
    @Test
    void duplicateResultHasNoSecondDomainEffect() {
        InboxMessageRepository inbox = mock(InboxMessageRepository.class);
        ProcessingJobRepository jobs = mock(ProcessingJobRepository.class);
        ProcessingEventRepository events = mock(ProcessingEventRepository.class);
        OutboxService outbox = mock(OutboxService.class);
        TranscriptImportService importer = mock(TranscriptImportService.class);
        when(inbox.claim(any(), anyString(), anyString(), any())).thenReturn(0);
        MediaResultService service = service(inbox, jobs, events, outbox, importer);

        assertThat(service.process(result(UUID.randomUUID(), UUID.randomUUID(), "correlation"), "{}"))
                .isFalse();
        verify(jobs, never()).findByIdForUpdate(any());
        verify(events, never()).save(any());
    }

    @Test
    void successfulValidationAdvancesToAudioExtractionAndEnqueuesCommand() {
        InboxMessageRepository inbox = mock(InboxMessageRepository.class);
        ProcessingJobRepository jobs = mock(ProcessingJobRepository.class);
        ProcessingEventRepository events = mock(ProcessingEventRepository.class);
        OutboxService outbox = mock(OutboxService.class);
        TranscriptImportService importer = mock(TranscriptImportService.class);
        Video video = Video.upload("video.mp4", "Video");
        video.completeIngestion("videos/" + video.getId() + "/original.mp4", BigDecimal.TEN,
                1920, 1080, BigDecimal.valueOf(30), "h264", "aac", null, null, null, null, null);
        ProcessingJob job = ProcessingJob.received(video, "key", "correlation");
        job.transitionTo(JobStatus.DOWNLOADING, 5, Instant.now());
        job.transitionTo(JobStatus.DOWNLOADED, 15, Instant.now());
        when(inbox.claim(any(), anyString(), anyString(), any())).thenReturn(1);
        when(jobs.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        MediaResultService service = service(inbox, jobs, events, outbox, importer);

        boolean processed = service.process(result(job.getId(), video.getId(), "correlation"), "{}");

        assertThat(processed).isTrue();
        assertThat(job.getStatus()).isEqualTo(JobStatus.EXTRACTING_AUDIO);
        verify(events).save(any());
        verify(outbox).enqueueAudioExtraction(job);
    }

    @Test
    void successfulExtractionRecordsArtifactAndQueuesTranscription() {
        InboxMessageRepository inbox = mock(InboxMessageRepository.class);
        ProcessingJobRepository jobs = mock(ProcessingJobRepository.class);
        ProcessingEventRepository events = mock(ProcessingEventRepository.class);
        OutboxService outbox = mock(OutboxService.class);
        TranscriptImportService importer = mock(TranscriptImportService.class);
        Video video = Video.upload("video.mp4", "Video");
        ProcessingJob job = ProcessingJob.received(video, "key", "correlation");
        job.transitionTo(JobStatus.DOWNLOADING, 5, Instant.now());
        job.transitionTo(JobStatus.DOWNLOADED, 15, Instant.now());
        job.transitionTo(JobStatus.EXTRACTING_AUDIO, 20, Instant.now());
        String audioKey = "jobs/" + job.getId() + "/audio/normalized.wav";
        when(inbox.claim(any(), anyString(), anyString(), any())).thenReturn(1);
        when(jobs.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        service(inbox, jobs, events, outbox, importer).process(
                result("EXTRACT_AUDIO", job.getId(), video.getId(), "correlation",
                        Map.of("audioStorageKey", audioKey)), "{}");

        assertThat(job.getStatus()).isEqualTo(JobStatus.TRANSCRIBING);
        assertThat(job.getNormalizedAudioPath()).isEqualTo(audioKey);
        verify(outbox).enqueueTranscription(job, audioKey);
    }

    @Test
    void partialRenderingCompletesWhenAtLeastOneOutputSucceeded() {
        InboxMessageRepository inbox = mock(InboxMessageRepository.class);
        ProcessingJobRepository jobs = mock(ProcessingJobRepository.class);
        ProcessingEventRepository events = mock(ProcessingEventRepository.class);
        OutboxService outbox = mock(OutboxService.class);
        TranscriptImportService transcriptImporter = mock(TranscriptImportService.class);
        ClipAnalysisImportService analysisImporter = mock(ClipAnalysisImportService.class);
        ClipSelectionService selector = mock(ClipSelectionService.class);
        ClipRenderImportService renderImporter = mock(ClipRenderImportService.class);
        Video video = Video.upload("video.mp4", "Video");
        ProcessingJob job = ProcessingJob.received(video, "key", "correlation");
        Instant now = Instant.now();
        for (var transition : new JobStatus[]{JobStatus.DOWNLOADING, JobStatus.DOWNLOADED,
                JobStatus.EXTRACTING_AUDIO, JobStatus.TRANSCRIBING, JobStatus.TRANSCRIBED,
                JobStatus.ANALYZING, JobStatus.ANALYZED, JobStatus.SELECTING_CLIPS,
                JobStatus.GENERATING_CLIPS}) {
            job.transitionTo(transition, Math.min(82, job.getProgress() + 9), now);
        }
        when(inbox.claim(any(), anyString(), anyString(), any())).thenReturn(1);
        when(jobs.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(renderImporter.importManifest(any(), anyString()))
                .thenReturn(new ClipRenderImportService.ImportResult(1, 1));
        MediaResultService service = new MediaResultService(inbox, jobs, events, outbox,
                transcriptImporter, analysisImporter, selector, renderImporter,
                mock(PipelineMetrics.class));
        String manifestKey = "jobs/" + job.getId() + "/render/manifest.json";

        service.process(result("RENDER_CLIPS", job.getId(), video.getId(), "correlation",
                Map.of("manifestStorageKey", manifestKey)), "{}");

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getProgress()).isEqualTo(100);
    }

    private MediaTaskResultV1 result(UUID jobId, UUID videoId, String correlationId) {
        return result("VALIDATE_MEDIA", jobId, videoId, correlationId, Map.of());
    }

    private MediaResultService service(InboxMessageRepository inbox, ProcessingJobRepository jobs,
                                       ProcessingEventRepository events, OutboxService outbox,
                                       TranscriptImportService importer) {
        return new MediaResultService(inbox, jobs, events, outbox, importer,
                mock(ClipAnalysisImportService.class), mock(ClipSelectionService.class),
                mock(ClipRenderImportService.class), mock(PipelineMetrics.class));
    }

    private MediaTaskResultV1 result(String taskType, UUID jobId, UUID videoId,
                                     String correlationId, Map<String, Object> details) {
        UUID messageId = UUID.randomUUID();
        return new MediaTaskResultV1(1, messageId, messageId, taskType, jobId, videoId,
                correlationId, MediaTaskResultV1.Status.SUCCEEDED, null, null, details, Instant.now());
    }
}
