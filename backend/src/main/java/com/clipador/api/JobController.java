package com.clipador.api;

import static com.clipador.api.ApiModels.JobProgressResponse;
import static com.clipador.api.ApiModels.JobResponse;
import static com.clipador.api.ApiModels.ClipCandidateResponse;
import static com.clipador.api.ApiModels.PageResponse;

import com.clipador.clip.ClipCandidateApplicationService;
import com.clipador.job.JobApplicationService;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
    private final JobApplicationService jobs;
    private final ClipCandidateApplicationService candidates;

    public JobController(JobApplicationService jobs, ClipCandidateApplicationService candidates) {
        this.jobs = jobs;
        this.candidates = candidates;
    }

    @GetMapping("/{id}")
    JobResponse findById(@PathVariable UUID id) {
        return JobResponse.from(jobs.findById(id));
    }

    @GetMapping("/{id}/progress")
    JobProgressResponse progress(@PathVariable UUID id) {
        return JobProgressResponse.from(jobs.findById(id));
    }

    @GetMapping("/{id}/candidates")
    PageResponse<ClipCandidateResponse> candidates(@PathVariable UUID id,
                                                    @RequestParam(required = false) Boolean selected,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        int boundedSize = Math.max(1, Math.min(size, 100));
        var result = candidates.findByJob(id, selected, PageRequest.of(Math.max(0, page), boundedSize,
                Sort.by(Sort.Direction.DESC, "finalScore")));
        return PageResponse.from(result.map(ClipCandidateResponse::from));
    }

    @PostMapping("/{id}/retry")
    JobResponse retry(@PathVariable UUID id) {
        return JobResponse.from(jobs.retry(id));
    }

    @PostMapping("/{id}/cancel")
    JobResponse cancel(@PathVariable UUID id) {
        return JobResponse.from(jobs.cancel(id));
    }
}
