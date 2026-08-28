package com.clipador.api;

import static com.clipador.api.ApiModels.*;

import com.clipador.clip.ClipApplicationService;
import com.clipador.job.JobApplicationService;
import com.clipador.video.VideoApplicationService;
import com.clipador.job.domain.ClipQuantityMode;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/videos")
public class VideoController {
    private final VideoApplicationService videos;
    private final JobApplicationService jobs;
    private final ClipApplicationService clips;

    public VideoController(VideoApplicationService videos, JobApplicationService jobs,
                           ClipApplicationService clips) {
        this.videos = videos;
        this.jobs = jobs;
        this.clips = clips;
    }

    @PostMapping("/youtube")
    ResponseEntity<RegistrationResponse> registerYoutube(
            @Valid @RequestBody YoutubeVideoRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String key = validIdempotencyKey(idempotencyKey);
        var registration = videos.registerYoutube(request.url(), request.title(), key,
                request.clipQuantityMode(), request.clipCount());
        var response = new RegistrationResponse(registration.video().getId(), registration.job().getId(),
                registration.job().getStatus(), registration.job().getCorrelationId(), registration.created());
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/jobs/" + registration.job().getId()))
                .body(response);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<RegistrationResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "clipQuantityMode", defaultValue = "AUTO") ClipQuantityMode clipQuantityMode,
            @RequestParam(name = "clipCount", required = false) Integer clipCount,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String key = validIdempotencyKey(idempotencyKey);
        try (InputStream input = file.getInputStream()) {
            var registration = videos.registerUpload(input, file.getSize(),
                    file.getOriginalFilename(), title, key, clipQuantityMode, clipCount);
            var response = new RegistrationResponse(registration.video().getId(), registration.job().getId(),
                    registration.job().getStatus(), registration.job().getCorrelationId(), registration.created());
            return ResponseEntity.accepted()
                    .location(URI.create("/api/v1/jobs/" + registration.job().getId()))
                    .body(response);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read uploaded file", exception);
        }
    }

    @GetMapping
    PageResponse<VideoResponse> findAll(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PageResponse.from(videos.findAll(pageable).map(VideoResponse::from));
    }

    @GetMapping("/{id}")
    VideoResponse findById(@PathVariable UUID id) {
        return VideoResponse.from(videos.findById(id));
    }

    @GetMapping("/{id}/jobs")
    PageResponse<JobResponse> findJobs(@PathVariable UUID id,
                                       @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        videos.findById(id);
        return PageResponse.from(jobs.findByVideo(id, pageable).map(JobResponse::from));
    }

    @GetMapping("/{id}/clips")
    PageResponse<ClipResponse> findClips(@PathVariable UUID id,
                                         @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        videos.findById(id);
        return PageResponse.from(clips.findByVideo(id, pageable).map(ClipResponse::from));
    }

    private String validIdempotencyKey(String supplied) {
        if (supplied == null || supplied.isBlank()) return UUID.randomUUID().toString();
        String value = supplied.trim();
        if (!value.matches("[A-Za-z0-9._:-]{8,200}")) {
            throw new IllegalArgumentException("Idempotency-Key must contain 8-200 safe characters");
        }
        return value;
    }
}
