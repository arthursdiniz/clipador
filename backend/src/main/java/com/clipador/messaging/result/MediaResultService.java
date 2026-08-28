package com.clipador.messaging.result;

import com.clipador.event.ProcessingEventRepository;
import com.clipador.event.domain.ProcessingEvent;
import com.clipador.job.ProcessingJobRepository;
import com.clipador.job.domain.JobStatus;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.messaging.contract.MediaTaskResultV1;
import com.clipador.messaging.contract.MediaTaskTypes;
import com.clipador.messaging.inbox.InboxMessageRepository;
import com.clipador.messaging.outbox.OutboxService;
import com.clipador.observability.PipelineMetrics;
import com.clipador.shared.api.ConflictException;
import com.clipador.shared.api.ResourceNotFoundException;
import com.clipador.transcript.TranscriptImportService;
import com.clipador.clip.ClipAnalysisImportService;
import com.clipador.clip.ClipSelectionService;
import com.clipador.clip.ClipRenderImportService;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaResultService {
    private final InboxMessageRepository inbox;
    private final ProcessingJobRepository jobs;
    private final ProcessingEventRepository events;
    private final OutboxService outbox;
    private final TranscriptImportService transcriptImporter;
    private final ClipAnalysisImportService analysisImporter;
    private final ClipSelectionService clipSelector;
    private final ClipRenderImportService renderImporter;
    private final PipelineMetrics metrics;
    private final Clock clock = Clock.systemUTC();

    public MediaResultService(InboxMessageRepository inbox, ProcessingJobRepository jobs,
                              ProcessingEventRepository events, OutboxService outbox,
                              TranscriptImportService transcriptImporter,
                              ClipAnalysisImportService analysisImporter,
                              ClipSelectionService clipSelector,
                              ClipRenderImportService renderImporter,
                              PipelineMetrics metrics) {
        this.inbox = inbox;
        this.jobs = jobs;
        this.events = events;
        this.outbox = outbox;
        this.transcriptImporter = transcriptImporter;
        this.analysisImporter = analysisImporter;
        this.clipSelector = clipSelector;
        this.renderImporter = renderImporter;
        this.metrics = metrics;
    }

    @Transactional
    public boolean process(MediaTaskResultV1 result, String rawPayload) {
        Instant now = clock.instant();
        if (inbox.claim(result.messageId(), "media-worker", rawPayload, now) == 0) return false;

        ProcessingJob job = jobs.findByIdForUpdate(result.jobId())
                .orElseThrow(() -> new ResourceNotFoundException("ProcessingJob", result.jobId()));
        if (!job.getVideo().getId().equals(result.videoId())) {
            throw new ConflictException("Worker result references a different video");
        }
        if (!job.getCorrelationId().equals(result.correlationId())) {
            throw new ConflictException("Worker result has an invalid correlation id");
        }

        try (MDC.MDCCloseable ignoredJob = MDC.putCloseable("jobId", job.getId().toString());
             MDC.MDCCloseable ignoredVideo = MDC.putCloseable("videoId", result.videoId().toString());
             MDC.MDCCloseable ignoredCorrelation = MDC.putCloseable("correlationId", result.correlationId())) {
            applyResult(job, result);
            metrics.recordWorkerResult(result.taskType(), result.status().name().toLowerCase());
        }
        return true;
    }

    @Transactional
    public void failUnprocessable(MediaTaskResultV1 result, String rawPayload, String cause) {
        Instant now = clock.instant();
        if (inbox.claim(result.messageId(), "media-worker-rejected", rawPayload, now) == 0) return;
        ProcessingJob job = jobs.findByIdForUpdate(result.jobId())
                .orElseThrow(() -> new ResourceNotFoundException("ProcessingJob", result.jobId()));
        if (job.getStatus().isTerminal()) return;
        JobStatus previous = job.getStatus();
        String message = "Backend could not apply worker result: "
                + (cause == null ? "unknown error" : cause);
        job.fail("RESULT_PROCESSING_FAILED", message, now);
        events.save(ProcessingEvent.of(job, previous, JobStatus.FAILED,
                job.getProgress(), message, now));
        metrics.recordTransition(job, previous, JobStatus.FAILED, now);
    }

    private void applyResult(ProcessingJob job, MediaTaskResultV1 result) {
        if (job.getStatus().isTerminal()) return;
        Instant now = clock.instant();
        if (result.status() == MediaTaskResultV1.Status.FAILED) {
            JobStatus previous = job.getStatus();
            String code = result.errorCode() == null ? result.taskType() + "_FAILED" : result.errorCode();
            String message = result.errorMessage() == null ? "Media worker task failed: " + result.taskType()
                    : result.errorMessage();
            job.fail(code, message, now);
            events.save(ProcessingEvent.of(job, previous, JobStatus.FAILED, job.getProgress(), message, now));
            metrics.recordTransition(job, previous, JobStatus.FAILED, now);
            return;
        }
        switch (result.taskType()) {
            case MediaTaskTypes.VALIDATE_MEDIA -> validationCompleted(job, now);
            case MediaTaskTypes.EXTRACT_AUDIO -> extractionCompleted(job, result, now);
            case MediaTaskTypes.TRANSCRIBE_AUDIO -> transcriptionCompleted(job, result, now);
            case MediaTaskTypes.ANALYZE_CONTENT -> analysisCompleted(job, result, now);
            case MediaTaskTypes.RENDER_CLIPS -> renderingCompleted(job, result, now);
            default -> throw new ConflictException("Unsupported media task result " + result.taskType());
        }
    }

    private void validationCompleted(ProcessingJob job, Instant now) {
        requireState(job, JobStatus.DOWNLOADED);
        transition(job, JobStatus.EXTRACTING_AUDIO, 20, "Original verified; audio extraction queued", now);
        outbox.enqueueAudioExtraction(job);
    }

    private void extractionCompleted(ProcessingJob job, MediaTaskResultV1 result, Instant now) {
        requireState(job, JobStatus.EXTRACTING_AUDIO);
        String audioStorageKey = requiredDetail(result, "audioStorageKey");
        requireJobArtifact(job, audioStorageKey, "/audio/normalized.wav");
        job.recordNormalizedAudio(audioStorageKey);
        transition(job, JobStatus.TRANSCRIBING, 35, "Normalized audio generated; transcription queued", now);
        outbox.enqueueTranscription(job, audioStorageKey);
    }

    private void transcriptionCompleted(ProcessingJob job, MediaTaskResultV1 result, Instant now) {
        requireState(job, JobStatus.TRANSCRIBING);
        String transcriptStorageKey = requiredDetail(result, "transcriptStorageKey");
        requireJobArtifact(job, transcriptStorageKey, "/transcript/transcript.json");
        TranscriptImportService.ImportResult imported = transcriptImporter.importArtifact(job, transcriptStorageKey);
        job.recordTranscriptArtifact(transcriptStorageKey);
        transition(job, JobStatus.TRANSCRIBED, 55,
                "Transcription persisted with " + imported.segmentCount() + " segments", now);
        transition(job, JobStatus.ANALYZING, 60, "Local multimodal clip analysis queued", now);
        outbox.enqueueContentAnalysis(job);
    }

    private void analysisCompleted(ProcessingJob job, MediaTaskResultV1 result, Instant now) {
        requireState(job, JobStatus.ANALYZING);
        String analysisStorageKey = requiredDetail(result, "analysisStorageKey");
        requireJobArtifact(job, analysisStorageKey, "/analysis/candidates.json");
        ClipAnalysisImportService.ImportResult imported = analysisImporter.importArtifact(job, analysisStorageKey);
        job.recordAnalysisArtifact(analysisStorageKey);
        transition(job, JobStatus.ANALYZED, 75,
                "Analysis persisted with " + imported.candidateCount() + " candidates", now);
        transition(job, JobStatus.SELECTING_CLIPS, 78, "Selecting diverse non-overlapping clips", now);
        var selectedCandidates = clipSelector.select(job);
        int selected = selectedCandidates.size();
        transition(job, JobStatus.GENERATING_CLIPS, 82,
                "Selected " + selected + " clips; rendering queued", now);
        outbox.enqueueClipRendering(job, selectedCandidates);
    }

    private void renderingCompleted(ProcessingJob job, MediaTaskResultV1 result, Instant now) {
        requireState(job, JobStatus.GENERATING_CLIPS);
        String manifestStorageKey = requiredDetail(result, "manifestStorageKey");
        requireJobArtifact(job, manifestStorageKey, "/render/manifest.json");
        ClipRenderImportService.ImportResult imported = renderImporter.importManifest(job, manifestStorageKey);
        metrics.recordRenders(imported.succeeded(), imported.failed());
        transition(job, JobStatus.GENERATING_SUBTITLES, 90,
                "Subtitle artifacts validated for " + imported.succeeded() + " renders", now);
        transition(job, JobStatus.RENDERING, 96,
                "Render manifest persisted; failed outputs: " + imported.failed(), now);
        if (imported.succeeded() == 0) {
            JobStatus previous = job.getStatus();
            job.fail("ALL_CLIP_RENDERS_FAILED", "No clip format could be rendered successfully", now);
            events.save(ProcessingEvent.of(job, previous, JobStatus.FAILED, job.getProgress(),
                    "All clip renders failed", now));
            metrics.recordTransition(job, previous, JobStatus.FAILED, now);
            return;
        }
        transition(job, JobStatus.COMPLETED, 100,
                "Completed with " + imported.succeeded() + " successful and "
                        + imported.failed() + " failed renders", now);
    }

    private String requiredDetail(MediaTaskResultV1 result, String name) {
        Object value = result.details().get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConflictException("Media task result is missing detail " + name);
        }
        return text;
    }

    private void requireJobArtifact(ProcessingJob job, String storageKey, String suffix) {
        String prefix = "jobs/" + job.getId();
        if (!storageKey.startsWith(prefix + "/") || !storageKey.endsWith(suffix)) {
            throw new ConflictException("Media task returned an unexpected artifact key");
        }
    }

    private void requireState(ProcessingJob job, JobStatus expected) {
        if (job.getStatus() != expected) {
            throw new ConflictException("Media task result is not valid in state " + job.getStatus());
        }
    }

    private void transition(ProcessingJob job, JobStatus target, int progress, String message, Instant now) {
        JobStatus previous = job.getStatus();
        job.transitionTo(target, progress, now);
        events.save(ProcessingEvent.of(job, previous, target, progress, message, now));
        metrics.recordTransition(job, previous, target, now);
    }
}
