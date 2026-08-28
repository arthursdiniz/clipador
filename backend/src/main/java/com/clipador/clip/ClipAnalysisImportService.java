package com.clipador.clip;

import com.clipador.clip.domain.ClipCandidate;
import com.clipador.clip.domain.ClipCategory;
import com.clipador.config.ClipAnalysisProperties;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.storage.StorageService;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ClipAnalysisImportService {
    private final StorageService storage;
    private final ClipCandidateRepository candidates;
    private final ObjectMapper objectMapper;
    private final ClipAnalysisProperties properties;

    public ClipAnalysisImportService(StorageService storage, ClipCandidateRepository candidates,
                                     ObjectMapper objectMapper, ClipAnalysisProperties properties) {
        this.storage = storage;
        this.candidates = candidates;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ImportResult importArtifact(ProcessingJob job, String storageKey) {
        long existing = candidates.countByJobId(job.getId());
        if (existing > 0) return new ImportResult(Math.toIntExact(existing));
        ClipAnalysisArtifactV1 artifact = read(storageKey);
        if (artifact.schemaVersion() != 1 || !job.getId().equals(artifact.jobId())
                || !job.getVideo().getId().equals(artifact.videoId())) {
            throw new IllegalArgumentException("Analysis artifact belongs to a different job or schema");
        }
        if (artifact.provider() == null || artifact.provider().isBlank()
                || artifact.candidates() == null || artifact.candidates().isEmpty()
                || artifact.candidates().size() > properties.maxCandidates()) {
            throw new IllegalArgumentException("Analysis artifact contains invalid candidates");
        }

        List<ClipCandidate> imported = new ArrayList<>(artifact.candidates().size());
        for (ClipAnalysisArtifactV1.Candidate source : artifact.candidates()) {
            validateWindow(source);
            imported.add(ClipCandidate.analyzed(job, source.candidateKey(), decimal(source.start()),
                    decimal(source.end()), score(source.semanticScore()), score(source.audioScore()),
                    score(source.visualScore()), score(source.narrativeScore()), score(source.hookScore()),
                    score(source.contextPenalty()), score(source.finalScore()), source.reason(), source.hook(),
                    category(source.category()), source.sourceText(), candidateTitle(source)));
        }
        candidates.saveAll(imported);
        candidates.flush();
        return new ImportResult(imported.size());
    }

    private ClipAnalysisArtifactV1 read(String storageKey) {
        try (InputStream input = storage.open(storageKey, properties.maxArtifactBytes())) {
            return objectMapper.readValue(input, ClipAnalysisArtifactV1.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not parse clip analysis artifact", exception);
        }
    }

    private void validateWindow(ClipAnalysisArtifactV1.Candidate source) {
        if (!Double.isFinite(source.start()) || !Double.isFinite(source.end()) || source.start() < 0
                || source.end() <= source.start()) {
            throw new IllegalArgumentException("Candidate timestamps are invalid");
        }
        double duration = source.end() - source.start();
        double tolerance = 1.0;
        if (duration + tolerance < properties.minDurationSeconds()
                || duration - tolerance > properties.maxDurationSeconds()) {
            throw new IllegalArgumentException("Candidate duration is outside configured limits");
        }
    }

    private BigDecimal decimal(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Candidate number must be finite");
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal score(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("Candidate score must be between 0 and 1");
        }
        return BigDecimal.valueOf(value).setScale(5, RoundingMode.HALF_UP);
    }

    private ClipCategory category(String value) {
        try {
            return ClipCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unknown clip category", exception);
        }
    }

    private String candidateTitle(ClipAnalysisArtifactV1.Candidate source) {
        String value = source.title();
        if (value == null || value.isBlank()) value = source.hook();
        if (value == null || value.isBlank()) value = source.sourceText();
        if (value == null || value.isBlank()) return "Momento em destaque";
        String normalized = value.trim().replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ");
        return normalized.substring(0, Math.min(normalized.length(), 160));
    }

    public record ImportResult(int candidateCount) {}
}
