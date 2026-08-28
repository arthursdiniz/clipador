package com.clipador.video.ingestion;

import com.clipador.config.IngestionProperties;
import com.clipador.media.ExternalProcessException;
import com.clipador.media.MediaMetadata;
import com.clipador.media.MediaProbe;
import com.clipador.media.MediaValidator;
import com.clipador.media.TemporaryDirectoryManager;
import com.clipador.storage.StorageService;
import com.clipador.storage.StoredObject;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class YoutubeIngestionListener {
    private static final Logger log = LoggerFactory.getLogger(YoutubeIngestionListener.class);

    private final YoutubeDownloadAdapter downloader;
    private final TemporaryDirectoryManager temporaryDirectories;
    private final MediaProbe mediaProbe;
    private final MediaValidator mediaValidator;
    private final MediaFilenamePolicy filenames;
    private final StorageService storage;
    private final VideoIngestionStateService states;
    private final IngestionProperties limits;

    public YoutubeIngestionListener(YoutubeDownloadAdapter downloader,
                                    TemporaryDirectoryManager temporaryDirectories,
                                    MediaProbe mediaProbe, MediaValidator mediaValidator,
                                    MediaFilenamePolicy filenames, StorageService storage,
                                    VideoIngestionStateService states, IngestionProperties limits) {
        this.downloader = downloader;
        this.temporaryDirectories = temporaryDirectories;
        this.mediaProbe = mediaProbe;
        this.mediaValidator = mediaValidator;
        this.filenames = filenames;
        this.storage = storage;
        this.states = states;
        this.limits = limits;
    }

    @Async("ingestionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void ingest(IngestionRequestedEvent event) {
        Path work = null;
        StoredObject stored = null;
        boolean completed = false;
        try (MDC.MDCCloseable ignoredCorrelation = MDC.putCloseable("correlationId", event.correlationId());
             MDC.MDCCloseable ignoredJob = MDC.putCloseable("jobId", event.jobId().toString());
             MDC.MDCCloseable ignoredVideo = MDC.putCloseable("videoId", event.videoId().toString())) {
            if (!states.start(event.jobId())) {
                log.info("Ignoring duplicate or obsolete ingestion request");
                return;
            }
            work = temporaryDirectories.create(event.jobId());
            DownloadedYoutubeVideo download = downloader.download(event.normalizedSourceUrl(), work);
            MediaMetadata media = mediaProbe.probe(download.path());
            mediaValidator.validate(media);
            String extension = filenames.downloadedExtension(download.path());
            String key = filenames.originalStorageKey(event.videoId(), extension);
            stored = storage.store(download.path(), key, limits.maxUploadBytes());
            states.complete(event.jobId(), event.videoId(), key, media, download.sourceMetadata());
            completed = true;
            log.info("YouTube ingestion completed");
        } catch (Exception exception) {
            String code = exception instanceof ExternalProcessException processException
                    ? processException.getErrorCode() : "INGESTION_FAILED";
            log.error("YouTube ingestion failed", exception);
            states.fail(event.jobId(), code, safeMessage(exception));
        } finally {
            if (!completed && stored != null) {
                try { storage.delete(stored.key()); } catch (RuntimeException cleanupError) {
                    log.warn("Could not remove incomplete stored video", cleanupError);
                }
            }
            if (work != null) {
                try { temporaryDirectories.cleanup(work); } catch (RuntimeException cleanupError) {
                    log.warn("Could not clean ingestion temporary directory", cleanupError);
                }
            }
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Video ingestion failed" : message;
    }
}
