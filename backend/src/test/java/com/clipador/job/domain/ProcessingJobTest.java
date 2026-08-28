package com.clipador.job.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clipador.shared.api.ConflictException;
import com.clipador.video.domain.Video;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProcessingJobTest {

    @Test
    void advancesAndCompletesWithMonotonicProgress() {
        ProcessingJob job = job();
        Instant now = Instant.parse("2026-08-25T12:00:00Z");

        job.transitionTo(JobStatus.DOWNLOADING, 5, now);
        job.transitionTo(JobStatus.DOWNLOADED, 10, now.plusSeconds(1));

        assertThat(job.getStatus()).isEqualTo(JobStatus.DOWNLOADED);
        assertThat(job.getProgress()).isEqualTo(10);
        assertThat(job.getStartedAt()).isEqualTo(now);
    }

    @Test
    void rejectsProgressRegressionAndSkippedStages() {
        ProcessingJob job = job();
        job.transitionTo(JobStatus.DOWNLOADING, 10, Instant.now());

        assertThatThrownBy(() -> job.transitionTo(JobStatus.DOWNLOADED, 5, Instant.now()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> job.transitionTo(JobStatus.ANALYZING, 20, Instant.now()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void preservesRequestedClipQuantityAndResolvedTarget() {
        ProcessingJob job = ProcessingJob.received(
                Video.upload("video.mp4", "Video"), "request-manual", "correlation-manual",
                ClipQuantityMode.MANUAL, 12);

        job.resolveTargetClipCount(12);

        assertThat(job.getClipQuantityMode()).isEqualTo(ClipQuantityMode.MANUAL);
        assertThat(job.getRequestedClipCount()).isEqualTo(12);
        assertThat(job.getTargetClipCount()).isEqualTo(12);
    }

    private ProcessingJob job() {
        Video video = Video.youtube("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "Video");
        return ProcessingJob.received(video, "request-123", "correlation-123");
    }
}
