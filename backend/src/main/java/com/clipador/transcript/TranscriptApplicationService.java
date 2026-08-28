package com.clipador.transcript;

import com.clipador.shared.api.ResourceNotFoundException;
import com.clipador.transcript.domain.Transcript;
import com.clipador.transcript.domain.TranscriptSegment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TranscriptApplicationService {
    private final TranscriptRepository transcripts;
    private final TranscriptSegmentRepository segments;

    public TranscriptApplicationService(TranscriptRepository transcripts,
                                        TranscriptSegmentRepository segments) {
        this.transcripts = transcripts;
        this.segments = segments;
    }

    @Transactional(readOnly = true)
    public Transcript findByJob(UUID jobId) {
        return transcripts.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Transcript for ProcessingJob", jobId));
    }

    @Transactional(readOnly = true)
    public Page<TranscriptSegment> findSegments(UUID jobId, Pageable pageable) {
        Transcript transcript = findByJob(jobId);
        return segments.findAllByTranscriptId(transcript.getId(), pageable);
    }
}
