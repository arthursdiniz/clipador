package com.clipador.video;

import com.clipador.config.IngestionProperties;
import com.clipador.clip.ClipQuantityPolicy;
import com.clipador.event.ProcessingEventRepository;
import com.clipador.event.domain.ProcessingEvent;
import com.clipador.job.ProcessingJobRepository;
import com.clipador.job.domain.JobStatus;
import com.clipador.job.domain.ClipQuantityMode;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.media.MediaMetadata;
import com.clipador.media.MediaProbe;
import com.clipador.media.MediaValidator;
import com.clipador.media.TemporaryDirectoryManager;
import com.clipador.messaging.outbox.OutboxService;
import com.clipador.observability.PipelineMetrics;
import com.clipador.shared.api.ResourceNotFoundException;
import com.clipador.storage.StorageService;
import com.clipador.storage.StoredObject;
import com.clipador.video.domain.Video;
import com.clipador.video.ingestion.IngestionRequestedEvent;
import com.clipador.video.ingestion.MediaFilenamePolicy;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.nio.file.Path;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class VideoApplicationService {
    private static final Logger log = LoggerFactory.getLogger(VideoApplicationService.class);

    private final VideoRepository videos;
    private final ProcessingJobRepository jobs;
    private final ProcessingEventRepository events;
    private final YouTubeUrlValidator urlValidator;
    private final MediaFilenamePolicy filenames;
    private final StorageService storage;
    private final MediaProbe mediaProbe;
    private final MediaValidator mediaValidator;
    private final TemporaryDirectoryManager temporaryDirectories;
    private final IngestionProperties limits;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactions;
    private final OutboxService outbox;
    private final PipelineMetrics metrics;
    private final ClipQuantityPolicy clipQuantityPolicy;
    private final Clock clock = Clock.systemUTC();

    public VideoApplicationService(VideoRepository videos, ProcessingJobRepository jobs,
                                   ProcessingEventRepository events, YouTubeUrlValidator urlValidator,
                                   MediaFilenamePolicy filenames, StorageService storage, MediaProbe mediaProbe,
                                   MediaValidator mediaValidator, TemporaryDirectoryManager temporaryDirectories,
                                   IngestionProperties limits,
                                   ApplicationEventPublisher eventPublisher, TransactionTemplate transactions,
                                   OutboxService outbox, PipelineMetrics metrics,
                                   ClipQuantityPolicy clipQuantityPolicy) {
        this.videos = videos;
        this.jobs = jobs;
        this.events = events;
        this.urlValidator = urlValidator;
        this.filenames = filenames;
        this.storage = storage;
        this.mediaProbe = mediaProbe;
        this.mediaValidator = mediaValidator;
        this.temporaryDirectories = temporaryDirectories;
        this.limits = limits;
        this.eventPublisher = eventPublisher;
        this.transactions = transactions;
        this.outbox = outbox;
        this.metrics = metrics;
        this.clipQuantityPolicy = clipQuantityPolicy;
    }

    public Registration registerYoutube(String sourceUrl, String title, String idempotencyKey) {
        return registerYoutube(sourceUrl, title, idempotencyKey, ClipQuantityMode.AUTO, null);
    }

    public Registration registerYoutube(String sourceUrl, String title, String idempotencyKey,
                                        ClipQuantityMode clipQuantityMode, Integer requestedClipCount) {
        clipQuantityPolicy.validateRequest(clipQuantityMode, requestedClipCount);
        String normalizedUrl = urlValidator.normalize(sourceUrl);
        Registration registration = required(transactions.execute(status -> {
            jobs.acquireIdempotencyLock(idempotencyKey);
            var existing = jobs.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) return existing(existing.get());

            Video video = videos.save(Video.youtube(normalizedUrl, normalizeTitle(title)));
            ProcessingJob job = jobs.save(ProcessingJob.received(
                    video, idempotencyKey, UUID.randomUUID().toString(),
                    clipQuantityMode, requestedClipCount));
            Instant now = clock.instant();
            events.save(ProcessingEvent.of(job, null, job.getStatus(), 0, "Job received", now));
            eventPublisher.publishEvent(new IngestionRequestedEvent(
                    job.getId(), video.getId(), job.getCorrelationId(), normalizedUrl));
            return new Registration(video, job, true);
        }));
        metrics.recordRegistration(registration.job(), registration.created());
        return registration;
    }

    public Registration registerUpload(InputStream input, long declaredSize, String originalFilename,
                                       String title, String idempotencyKey) {
        return registerUpload(input, declaredSize, originalFilename, title, idempotencyKey,
                ClipQuantityMode.AUTO, null);
    }

    public Registration registerUpload(InputStream input, long declaredSize, String originalFilename,
                                       String title, String idempotencyKey,
                                       ClipQuantityMode clipQuantityMode, Integer requestedClipCount) {
        clipQuantityPolicy.validateRequest(clipQuantityMode, requestedClipCount);
        Registration duplicate = transactions.execute(status -> jobs.findByIdempotencyKey(idempotencyKey)
                .map(this::existing).orElse(null));
        if (duplicate != null) {
            metrics.recordRegistration(duplicate.job(), false);
            return duplicate;
        }
        if (declaredSize <= 0) throw new IllegalArgumentException("Upload is empty");
        if (declaredSize > limits.maxUploadBytes()) {
            throw new IllegalArgumentException("File exceeds the configured size limit");
        }

        MediaFilenamePolicy.UploadFilename uploadName = filenames.validateUploadFilename(originalFilename);
        String effectiveTitle = normalizeTitle(title);
        if (effectiveTitle == null) effectiveTitle = titleFromFilename(uploadName.filename());
        Video video = Video.upload(uploadName.filename(), effectiveTitle);
        String key = filenames.originalStorageKey(video.getId(), uploadName.extension());
        Path work = temporaryDirectories.create(video.getId());
        StoredObject stored = null;
        try {
            Path staged = temporaryDirectories.stage(input, work,
                    "upload." + uploadName.extension(), limits.maxUploadBytes());
            MediaMetadata metadata = mediaProbe.probe(staged);
            mediaValidator.validate(metadata);
            stored = storage.store(staged, key, limits.maxUploadBytes());
            video.completeIngestion(key, metadata.durationSeconds(), metadata.width(), metadata.height(),
                    metadata.fps(), metadata.videoCodec(), metadata.audioCodec(), null, null, null, null, null);

            Registration registration = required(transactions.execute(status -> persistUploadedVideo(
                    video, idempotencyKey, clipQuantityMode, requestedClipCount)));
            if (!registration.created()) storage.delete(key);
            metrics.recordRegistration(registration.job(), registration.created());
            return registration;
        } catch (RuntimeException exception) {
            if (stored != null) {
                try { storage.delete(key); } catch (RuntimeException cleanupError) {
                    exception.addSuppressed(cleanupError);
                }
            }
            throw exception;
        } finally {
            try { temporaryDirectories.cleanup(work); } catch (RuntimeException cleanupError) {
                log.warn("Could not clean upload staging directory", cleanupError);
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<Video> findAll(Pageable pageable) {
        return videos.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Video findById(UUID id) {
        return videos.findById(id).orElseThrow(() -> new ResourceNotFoundException("Video", id));
    }

    private Registration persistUploadedVideo(Video video, String idempotencyKey,
                                               ClipQuantityMode clipQuantityMode,
                                               Integer requestedClipCount) {
        jobs.acquireIdempotencyLock(idempotencyKey);
        var existing = jobs.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing(existing.get());

        videos.save(video);
        ProcessingJob job = jobs.save(ProcessingJob.received(
                video, idempotencyKey, UUID.randomUUID().toString(),
                clipQuantityMode, requestedClipCount));
        Instant now = clock.instant();
        events.save(ProcessingEvent.of(job, null, JobStatus.RECEIVED, 0, "Job received", now));
        job.transitionTo(JobStatus.DOWNLOADING, 5, now);
        metrics.recordTransition(job, JobStatus.RECEIVED, JobStatus.DOWNLOADING, now);
        events.save(ProcessingEvent.of(job, JobStatus.RECEIVED, JobStatus.DOWNLOADING, 5,
                "Upload received", now));
        job.transitionTo(JobStatus.DOWNLOADED, 15, now);
        metrics.recordTransition(job, JobStatus.DOWNLOADING, JobStatus.DOWNLOADED, now);
        events.save(ProcessingEvent.of(job, JobStatus.DOWNLOADING, JobStatus.DOWNLOADED, 15,
                "Upload stored and validated", now));
        outbox.enqueueMediaValidation(job, video, video.getStoragePath());
        return new Registration(video, job, true);
    }

    private Registration existing(ProcessingJob job) {
        return new Registration(job.getVideo(), job, false);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) return null;
        String normalized = title.trim();
        if (normalized.length() > 512) throw new IllegalArgumentException("Title must not exceed 512 characters");
        return normalized;
    }

    private String titleFromFilename(String filename) {
        int separator = filename.lastIndexOf('.');
        return separator > 0 ? filename.substring(0, separator) : filename;
    }

    private <T> T required(T value) {
        if (value == null) throw new IllegalStateException("Transaction completed without a result");
        return value;
    }

    public record Registration(Video video, ProcessingJob job, boolean created) {}
}
