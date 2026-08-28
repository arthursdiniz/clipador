package com.clipador.job;

import com.clipador.event.ProcessingEventRepository;
import com.clipador.event.domain.ProcessingEvent;
import com.clipador.job.domain.JobStatus;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.messaging.outbox.OutboxService;
import com.clipador.observability.PipelineMetrics;
import com.clipador.shared.api.ConflictException;
import com.clipador.shared.api.ResourceNotFoundException;
import com.clipador.video.domain.VideoSourceType;
import com.clipador.video.ingestion.IngestionRequestedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobApplicationService {

    private final ProcessingJobRepository jobs;
    private final ProcessingEventRepository events;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxService outbox;
    private final PipelineMetrics metrics;

    @Autowired
    public JobApplicationService(ProcessingJobRepository jobs, ProcessingEventRepository events,
                                 ApplicationEventPublisher eventPublisher, OutboxService outbox,
                                 PipelineMetrics metrics) {
        this(jobs, events, eventPublisher, outbox, metrics, Clock.systemUTC());
    }

    JobApplicationService(ProcessingJobRepository jobs, ProcessingEventRepository events,
                          ApplicationEventPublisher eventPublisher, OutboxService outbox,
                          PipelineMetrics metrics, Clock clock) {
        this.jobs = jobs;
        this.events = events;
        this.eventPublisher = eventPublisher;
        this.outbox = outbox;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProcessingJob findById(UUID id) {
        return jobs.findById(id).orElseThrow(() -> new ResourceNotFoundException("ProcessingJob", id));
    }

    @Transactional(readOnly = true)
    public Page<ProcessingJob> findByVideo(UUID videoId, Pageable pageable) {
        return jobs.findAllByVideoId(videoId, pageable);
    }

    @Transactional
    public ProcessingJob retry(UUID id) {
        ProcessingJob job = locked(id);
        if (job.getStatus() != JobStatus.FAILED) {
            throw new ConflictException("Only failed jobs can be retried");
        }
        JobStatus previous = job.getStatus();
        Instant now = clock.instant();
        job.transitionTo(JobStatus.RECEIVED, 0, now);
        events.save(ProcessingEvent.of(job, previous, job.getStatus(), job.getProgress(), "Retry requested", now));
        metrics.recordRetry(job);
        metrics.recordTransition(job, previous, JobStatus.RECEIVED, now);
        if (job.getVideo().getStoragePath() != null) {
            transition(job, JobStatus.DOWNLOADING, 5, "Stored media prepared for retry", now);
            transition(job, JobStatus.DOWNLOADED, 15, "Stored media ready for validation retry", now);
            outbox.enqueueMediaValidation(job, job.getVideo(), job.getVideo().getStoragePath());
        } else if (job.getVideo().getSourceType() == VideoSourceType.YOUTUBE) {
            eventPublisher.publishEvent(new IngestionRequestedEvent(job.getId(), job.getVideo().getId(),
                    job.getCorrelationId(), job.getVideo().getSourceUrl()));
        }
        return job;
    }

    @Transactional
    public ProcessingJob cancel(UUID id) {
        ProcessingJob job = locked(id);
        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.CANCELLED) {
            throw new ConflictException("Completed or cancelled jobs cannot be cancelled");
        }
        JobStatus previous = job.getStatus();
        Instant now = clock.instant();
        job.transitionTo(JobStatus.CANCELLED, job.getProgress(), now);
        events.save(ProcessingEvent.of(job, previous, job.getStatus(), job.getProgress(), "Cancellation requested", now));
        metrics.recordTransition(job, previous, JobStatus.CANCELLED, now);
        return job;
    }

    private ProcessingJob locked(UUID id) {
        return jobs.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProcessingJob", id));
    }

    private void transition(ProcessingJob job, JobStatus target, int progress, String message, Instant now) {
        JobStatus previous = job.getStatus();
        job.transitionTo(target, progress, now);
        events.save(ProcessingEvent.of(job, previous, target, progress, message, now));
        metrics.recordTransition(job, previous, target, now);
    }
}
