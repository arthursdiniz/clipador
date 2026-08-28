package com.clipador.clip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clipador.clip.domain.Clip;
import com.clipador.clip.domain.ClipCandidate;
import com.clipador.clip.domain.ClipCategory;
import com.clipador.clip.domain.ClipFormat;
import com.clipador.config.RenderingProperties;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.storage.StorageService;
import com.clipador.video.domain.Video;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClipApplicationServiceTest {
    @Test
    void usesTheEngagementTitleAsASafeDownloadFilename() {
        ProcessingJob job = ProcessingJob.received(Video.upload("video.mp4", "Video"), "key", "correlation");
        ClipCandidate candidate = ClipCandidate.analyzed(job, "candidate", BigDecimal.TEN,
                BigDecimal.valueOf(55), score(.9), score(.7), score(.6), score(.8), score(.9),
                score(.1), score(.85), "Complete", "Hook", ClipCategory.INSIGHT, "Source text",
                "Eu perdi tudo em 3 meses: e recomecei!");
        Clip clip = Clip.rendered(job, candidate, ClipFormat.VERTICAL_9_16, 1080, 1920,
                BigDecimal.valueOf(45), "jobs/clip.mp4", null, null, null, null);
        ClipRepository repository = mock(ClipRepository.class);
        when(repository.findById(clip.getId())).thenReturn(Optional.of(clip));
        StorageService storage = mock(StorageService.class);
        when(storage.open("jobs/clip.mp4", 1_048_576)).thenReturn(new ByteArrayInputStream(new byte[] {1}));
        RenderingProperties properties = new RenderingProperties(List.of(ClipFormat.VERTICAL_9_16),
                true, 21, "medium", 160, 30, 1024, 1_048_576);

        var download = new ClipApplicationService(repository, storage, properties).download(clip.getId());

        assertThat(download.filename()).isEqualTo("eu-perdi-tudo-em-3-meses-e-recomecei.mp4");
    }

    private BigDecimal score(double value) {
        return BigDecimal.valueOf(value);
    }
}
