package com.clipador.api;

import static com.clipador.api.ApiModels.ClipResponse;

import com.clipador.clip.ClipApplicationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/clips")
public class ClipController {
    private final ClipApplicationService clips;

    public ClipController(ClipApplicationService clips) {
        this.clips = clips;
    }

    @GetMapping("/{id}")
    ClipResponse findById(@PathVariable UUID id) {
        return ClipResponse.from(clips.findById(id));
    }

    @GetMapping(value = "/{id}/download", produces = "video/mp4")
    ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id) {
        ClipApplicationService.ClipDownload download = clips.download(id);
        StreamingResponseBody body = output -> {
            try (var input = download.input()) {
                input.transferTo(output);
            }
        };
        String disposition = ContentDisposition.attachment().filename(download.filename()).build().toString();
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp4"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(body);
    }
}
