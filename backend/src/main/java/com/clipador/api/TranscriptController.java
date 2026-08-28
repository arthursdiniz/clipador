package com.clipador.api;

import com.clipador.transcript.TranscriptApplicationService;
import com.clipador.transcript.domain.Transcript;
import com.clipador.transcript.domain.TranscriptSegment;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/transcript")
public class TranscriptController {
    private final TranscriptApplicationService service;
    private final ObjectMapper objectMapper;

    public TranscriptController(TranscriptApplicationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<TranscriptResponse> get(@PathVariable UUID jobId) {
        return ResponseEntity.ok(TranscriptResponse.from(service.findByJob(jobId)));
    }

    @GetMapping("/segments")
    public ResponseEntity<Page<TranscriptSegmentResponse>> segments(@PathVariable UUID jobId,
                                                                    Pageable pageable) {
        return ResponseEntity.ok(service.findSegments(jobId, pageable).map(this::toResponse));
    }

    private TranscriptSegmentResponse toResponse(TranscriptSegment segment) {
        JsonNode words = null;
        if (segment.getWordsJson() != null) {
            try {
                words = objectMapper.readTree(segment.getWordsJson());
            } catch (JacksonException exception) {
                throw new IllegalStateException("Persisted word timestamps are invalid", exception);
            }
        }
        return new TranscriptSegmentResponse(segment.getId(), segment.getSegmentIndex(),
                segment.getStartTime(), segment.getEndTime(), segment.getText(),
                segment.getConfidence(), segment.getSpeakerLabel(), words);
    }

    public record TranscriptResponse(UUID id, String detectedLanguage, BigDecimal languageProbability,
                                     String engine, String modelName, boolean wordTimestamps,
                                     String fullText) {
        static TranscriptResponse from(Transcript transcript) {
            return new TranscriptResponse(transcript.getId(), transcript.getDetectedLanguage(),
                    transcript.getLanguageProbability(), transcript.getEngine(), transcript.getModelName(),
                    transcript.isWordTimestamps(), transcript.getFullText());
        }
    }

    public record TranscriptSegmentResponse(UUID id, int index, BigDecimal start, BigDecimal end,
                                            String text, BigDecimal confidence, String speaker,
                                            JsonNode words) {}
}
