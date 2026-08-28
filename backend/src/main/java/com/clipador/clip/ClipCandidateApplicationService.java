package com.clipador.clip;

import com.clipador.clip.domain.ClipCandidate;
import com.clipador.job.ProcessingJobRepository;
import com.clipador.shared.api.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClipCandidateApplicationService {
    private final ProcessingJobRepository jobs;
    private final ClipCandidateRepository candidates;

    public ClipCandidateApplicationService(ProcessingJobRepository jobs,
                                           ClipCandidateRepository candidates) {
        this.jobs = jobs;
        this.candidates = candidates;
    }

    @Transactional(readOnly = true)
    public Page<ClipCandidate> findByJob(UUID jobId, Boolean selected, Pageable pageable) {
        if (!jobs.existsById(jobId)) throw new ResourceNotFoundException("ProcessingJob", jobId);
        return selected == null ? candidates.findAllByJobId(jobId, pageable)
                : candidates.findAllByJobIdAndSelected(jobId, selected, pageable);
    }
}
