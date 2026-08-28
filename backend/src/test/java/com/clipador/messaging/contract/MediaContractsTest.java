package com.clipador.messaging.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import com.clipador.clip.domain.ClipFormat;
import com.clipador.config.ReframingMode;
import org.junit.jupiter.api.Test;

class MediaContractsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void commandSerializesAsVersionedCamelCaseContract() throws Exception {
        UUID messageId = UUID.randomUUID();
        var command = new MediaValidationCommandV1(1, messageId, "VALIDATE_MEDIA",
                UUID.randomUUID(), UUID.randomUUID(), "correlation-1",
                "videos/id/original.mp4", 1, Instant.parse("2026-08-25T12:00:00Z"));

        String json = mapper.writeValueAsString(command);

        assertThat(json).contains("\"schemaVersion\":1", "\"messageId\":\"" + messageId + "\"");
    }

    @Test
    void resultRejectsUnsupportedSchema() {
        assertThatThrownBy(() -> new MediaTaskResultV1(2, UUID.randomUUID(), UUID.randomUUID(),
                "VALIDATE_MEDIA", UUID.randomUUID(), UUID.randomUUID(), "correlation",
                MediaTaskResultV1.Status.SUCCEEDED, null, null, Map.of(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void smartRenderContractSerializesAllBoundedOptions() throws Exception {
        var command = new RenderClipsCommandV2(2, UUID.randomUUID(), "RENDER_CLIPS",
                UUID.randomUUID(), UUID.randomUUID(), "correlation", "videos/id/original.mp4",
                "jobs/id/transcript/transcript.json", "jobs/id/render/manifest.json",
                List.of(new RenderClipsCommandV1.CandidateSpec(UUID.randomUUID(), 10, 40)),
                List.of(new RenderClipsCommandV1.FormatSpec(ClipFormat.VERTICAL_9_16, 1080, 1920)),
                true, 21, "medium", 160, 30, true, ReframingMode.AUTO, 1.5, .82,
                .35, .025, 640, 64, 1, Instant.now());

        String json = mapper.writeValueAsString(command);

        assertThat(json).contains("\"schemaVersion\":2", "\"smartReframingEnabled\":true",
                "\"reframingMode\":\"AUTO\"");
    }
}
