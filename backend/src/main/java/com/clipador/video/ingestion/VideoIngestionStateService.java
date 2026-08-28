package com.clipador.video.ingestion;

import com.clipador.event.ProcessingEventRepository;
import com.clipador.event.domain.ProcessingEvent;
import com.clipador.job.ProcessingJobRepository;
import com.clipador.job.domain.JobStatus;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.media.MediaMetadata;
import com.clipador.messaging.outbox.OutboxService;
import com.clipador.observability.PipelineMetrics;
import com.clipador.shared.api.ConflictException;
import com.clipador.shared.api.ResourceNotFoundException;
import com.clipador.video.VideoRepository;
import com.clipador.video.domain.Video;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoIngestionStateService {
    private final ProcessingJobRepository jobs;
    private final VideoRepository videos;
    private final ProcessingEventRepository events;
    private final Clock clock;
    private final OutboxService outbox;
    private final PipelineMetrics metrics;

    public VideoIngestionStateService(ProcessingJobRepository jobs, VideoRepository videos,
                                      ProcessingEventRepository events, OutboxService outbox,
                                      PipelineMetrics metrics) {
        this.jobs = jobs;
        this.videos = videos;
        this.events = events;
        this.outbox = outbox;
        this.metrics = metrics;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public boolean start(UUID jobId) {
        ProcessingJob job = locked(jobId);
        if (job.getStatus() != JobStatus.RECEIVED) return false;
        transition(job, JobStatus.DOWNLOADING, 5, "Video acquisition started");
        return true;
    }

    @Transactional
    public void complete(UUID jobId, UUID videoId, String storageKey, MediaMetadata media,
                         YoutubeSourceMetadata source) {
        ProcessingJob job = locked(jobId);
        if (!job.getVideo().getId().equals(videoId)) throw new ConflictException("Job does not belong to video");
        Video video = videos.findById(videoId).orElseThrow(() -> new ResourceNotFoundException("Video", videoId));
        video.completeIngestion(storageKey, media.durationSeconds(), media.width(), media.height(), media.fps(),
                media.videoCodec(), media.audioCodec(), source == null ? null : source.title(),
                source == null ? null : source.channel(), source == null ? null : source.thumbnailUrl(),
                source == null ? null : source.description(), source == null ? null : source.language());
        transition(job, JobStatus.DOWNLOADED, 15, "Video acquired and validated");
        outbox.enqueueMediaValidation(job, video, storageKey);
    }

    @Transactional
    public void fail(UUID jobId, String code, String message) {
        ProcessingJob job = locked(jobId);
        if (job.getStatus().isTerminal()) return;
        JobStatus previous = job.getStatus();
        Instant now = clock.instant();
        job.fail(code, message, now);
        events.save(ProcessingEvent.of(job, previous, JobStatus.FAILED, job.getProgress(), message, now));
        metrics.recordTransition(job, previous, JobStatus.FAILED, now);
    }

    private void transition(ProcessingJob job, JobStatus target, int progress, String message) {
        JobStatus previous = job.getStatus();
        Instant now = clock.instant();
        job.transitionTo(target, progress, now);
        events.save(ProcessingEvent.of(job, previous, target, progress, message, now));
        metrics.recordTransition(job, previous, target, now);
    }

    private ProcessingJob locked(UUID id) {
        return jobs.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProcessingJob", id));
    }
}
