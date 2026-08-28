package com.clipador.clip;

import com.clipador.clip.domain.Clip;
import com.clipador.clip.domain.ClipCandidate;
import com.clipador.clip.domain.ClipFormat;
import com.clipador.config.RenderingProperties;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.storage.StorageService;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ClipRenderImportService {
    private final StorageService storage;
    private final ClipRepository clips;
    private final ClipCandidateRepository candidates;
    private final ObjectMapper objectMapper;
    private final RenderingProperties properties;

    public ClipRenderImportService(StorageService storage, ClipRepository clips,
                                   ClipCandidateRepository candidates, ObjectMapper objectMapper,
                                   RenderingProperties properties) {
        this.storage = storage;
        this.clips = clips;
        this.candidates = candidates;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ImportResult importManifest(ProcessingJob job, String storageKey) {
        ClipRenderManifestV1 manifest = read(storageKey);
        if (manifest.schemaVersion() != 1 || !job.getId().equals(manifest.jobId())
                || !job.getVideo().getId().equals(manifest.videoId()) || manifest.renders() == null) {
            throw new IllegalArgumentException("Render manifest belongs to a different job or schema");
        }
        Map<UUID, ClipCandidate> selected = new HashMap<>();
        candidates.findAllByJobIdAndSelectedTrueOrderByFinalScoreDesc(job.getId())
                .forEach(candidate -> selected.put(candidate.getId(), candidate));
        int expected = selected.size() * properties.formats().size();
        if (selected.isEmpty() || manifest.renders().size() != expected) {
            throw new IllegalArgumentException("Render manifest has an unexpected number of outputs");
        }

        Set<String> pairs = new HashSet<>();
        int succeeded = 0;
        int failed = 0;
        for (ClipRenderManifestV1.Render render : manifest.renders()) {
            ClipCandidate candidate = selected.get(render.candidateId());
            ClipFormat format = parseFormat(render.format());
            String pair = render.candidateId() + ":" + format;
            if (candidate == null || !properties.formats().contains(format) || !pairs.add(pair)) {
                throw new IllegalArgumentException("Render manifest references an unexpected output");
            }
            validateDimensions(format, render.width(), render.height());
            validateReframing(render.reframing());
            BigDecimal duration = duration(render.durationSeconds(), candidate);
            if (clips.findByCandidateIdAndFormat(candidate.getId(), format).isPresent()) {
                if ("SUCCEEDED".equals(render.status())) succeeded++; else failed++;
                continue;
            }
            if ("SUCCEEDED".equals(render.status())) {
                requireArtifact(job, candidate, format, render.storageKey(), ".mp4");
                requireArtifact(job, candidate, format, render.srtStorageKey(), ".srt");
                requireArtifact(job, candidate, format, render.vttStorageKey(), ".vtt");
                requireArtifact(job, candidate, format, render.assStorageKey(), ".ass");
                requireArtifact(job, candidate, format, render.thumbnailStorageKey(), ".jpg");
                verifyReadable(render.storageKey(), properties.maxDownloadBytes());
                verifyReadable(render.srtStorageKey(), properties.maxManifestBytes());
                verifyReadable(render.vttStorageKey(), properties.maxManifestBytes());
                verifyReadable(render.assStorageKey(), properties.maxManifestBytes());
                verifyReadable(render.thumbnailStorageKey(), properties.maxManifestBytes());
                clips.save(Clip.rendered(job, candidate, format, render.width(), render.height(), duration,
                        render.storageKey(), render.srtStorageKey(), render.vttStorageKey(),
                        render.assStorageKey(), render.thumbnailStorageKey()));
                succeeded++;
            } else if ("FAILED".equals(render.status())) {
                if (render.errorCode() == null || render.errorCode().isBlank()
                        || render.errorMessage() == null || render.errorMessage().isBlank()) {
                    throw new IllegalArgumentException("Failed render must contain an error");
                }
                String error = render.errorCode() + ": " + render.errorMessage();
                clips.save(Clip.failed(job, candidate, format, render.width(), render.height(), duration, error));
                failed++;
            } else {
                throw new IllegalArgumentException("Unknown render status");
            }
        }
        clips.flush();
        return new ImportResult(succeeded, failed);
    }

    private ClipRenderManifestV1 read(String storageKey) {
        try (InputStream input = storage.open(storageKey, properties.maxManifestBytes())) {
            return objectMapper.readValue(input, ClipRenderManifestV1.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not parse render manifest", exception);
        }
    }

    private void validateDimensions(ClipFormat format, int width, int height) {
        if (width != properties.width(format) || height != properties.height(format)) {
            throw new IllegalArgumentException("Render dimensions differ from the requested format");
        }
    }

    private void validateReframing(ClipRenderManifestV1.Reframing reframing) {
        if (reframing == null) return; // Phase 6 manifests remain importable.
        if (reframing.strategy() == null || reframing.strategy().isBlank()
                || !Double.isFinite(reframing.faceDetectionCoverage())
                || reframing.faceDetectionCoverage() < 0 || reframing.faceDetectionCoverage() > 1
                || !Double.isFinite(reframing.subjectDetectionCoverage())
                || reframing.subjectDetectionCoverage() < 0 || reframing.subjectDetectionCoverage() > 1
                || reframing.keyframeCount() < 0 || reframing.keyframeCount() > 256) {
            throw new IllegalArgumentException("Render manifest contains invalid reframing metadata");
        }
    }

    private BigDecimal duration(double value, ClipCandidate candidate) {
        double expected = candidate.getEndTime().subtract(candidate.getStartTime()).doubleValue();
        if (!Double.isFinite(value) || value <= 0 || Math.abs(value - expected) > 0.1) {
            throw new IllegalArgumentException("Render duration differs from its candidate");
        }
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    private ClipFormat parseFormat(String value) {
        try { return ClipFormat.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Unknown render format", exception); }
    }

    private void requireArtifact(ProcessingJob job, ClipCandidate candidate, ClipFormat format,
                                 String key, String extension) {
        String prefix = "jobs/" + job.getId() + "/clips/" + candidate.getId() + "/";
        String formatToken = format.name().toLowerCase(Locale.ROOT);
        if (key == null || !key.startsWith(prefix) || !key.contains("/" + formatToken + "/")
                || !key.endsWith(extension)) {
            throw new IllegalArgumentException("Render manifest contains an unexpected artifact key");
        }
    }

    private void verifyReadable(String storageKey, long limit) {
        try (InputStream ignored = storage.open(storageKey, limit)) {
            // Opening performs bounded size and regular-file validation.
        } catch (IOException exception) {
            throw new IllegalArgumentException("Rendered clip could not be opened", exception);
        }
    }

    public record ImportResult(int succeeded, int failed) {}
}
