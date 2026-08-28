package com.clipador.transcript;

import com.clipador.config.TranscriptionProperties;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.storage.StorageService;
import com.clipador.transcript.domain.Transcript;
import com.clipador.transcript.domain.TranscriptSegment;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TranscriptImportService {
    private final StorageService storage;
    private final TranscriptRepository transcripts;
    private final TranscriptSegmentRepository segments;
    private final ObjectMapper objectMapper;
    private final TranscriptionProperties properties;

    public TranscriptImportService(StorageService storage, TranscriptRepository transcripts,
                                   TranscriptSegmentRepository segments, ObjectMapper objectMapper,
                                   TranscriptionProperties properties) {
        this.storage = storage;
        this.transcripts = transcripts;
        this.segments = segments;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ImportResult importArtifact(ProcessingJob job, String storageKey) {
        TranscriptArtifactV1 artifact = read(storageKey);
        validateEnvelope(job, artifact);
        var existing = transcripts.findByJobId(job.getId());
        if (existing.isPresent()) {
            long count = segments.countByTranscriptId(existing.get().getId());
            return new ImportResult(existing.get(), Math.toIntExact(count));
        }

        Transcript transcript = transcripts.save(Transcript.imported(job, artifact.detectedLanguage(),
                decimal(artifact.languageProbability()), artifact.engine(), artifact.modelName(),
                artifact.wordTimestamps(), normalizeFullText(artifact)));
        List<TranscriptArtifactV1.Segment> sourceSegments = artifact.segments();
        if (sourceSegments == null || sourceSegments.isEmpty()) {
            throw new IllegalArgumentException("Transcript artifact contains no speech segments");
        }
        if (sourceSegments.size() > properties.maxSegments()) {
            throw new IllegalArgumentException("Transcript artifact exceeds the segment limit");
        }

        List<TranscriptSegment> batch = new ArrayList<>(properties.persistenceBatchSize());
        int expectedIndex = 0;
        for (TranscriptArtifactV1.Segment source : sourceSegments) {
            if (source.index() != expectedIndex++) {
                throw new IllegalArgumentException("Transcript segment indexes must be contiguous");
            }
            batch.add(TranscriptSegment.imported(transcript, source.index(),
                    decimal(source.start()), decimal(source.end()), source.text(),
                    decimal(source.confidence()), wordsJson(source.words())));
            if (batch.size() == properties.persistenceBatchSize()) {
                segments.saveAll(batch);
                segments.flush();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            segments.saveAll(batch);
            segments.flush();
        }
        return new ImportResult(transcript, sourceSegments.size());
    }

    private TranscriptArtifactV1 read(String storageKey) {
        try (InputStream input = storage.open(storageKey, properties.maxArtifactBytes())) {
            return objectMapper.readValue(input, TranscriptArtifactV1.class);
        } catch (IOException | JacksonException exception) {
            throw new IllegalArgumentException("Could not parse transcript artifact", exception);
        }
    }

    private void validateEnvelope(ProcessingJob job, TranscriptArtifactV1 artifact) {
        if (artifact == null || artifact.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported transcript artifact schema");
        }
        if (!job.getId().equals(artifact.jobId()) || !job.getVideo().getId().equals(artifact.videoId())) {
            throw new IllegalArgumentException("Transcript artifact belongs to a different job or video");
        }
        if (!artifact.wordTimestamps()) {
            throw new IllegalArgumentException("Transcript artifact must contain word timestamps");
        }
    }

    private String normalizeFullText(TranscriptArtifactV1 artifact) {
        if (artifact.fullText() != null && !artifact.fullText().isBlank()) return artifact.fullText().trim();
        if (artifact.segments() == null) return null;
        return artifact.segments().stream().map(TranscriptArtifactV1.Segment::text)
                .filter(text -> text != null && !text.isBlank())
                .map(String::trim).collect(java.util.stream.Collectors.joining(" "));
    }

    private String wordsJson(List<TranscriptArtifactV1.Word> words) {
        if (words == null || words.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(words);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not serialize transcript words", exception);
        }
    }

    private BigDecimal decimal(Double value) {
        if (value == null) return null;
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Numeric value must be finite");
        return BigDecimal.valueOf(value);
    }

    private BigDecimal decimal(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Timestamp must be finite");
        return BigDecimal.valueOf(value);
    }

    public record ImportResult(Transcript transcript, int segmentCount) {}
}
