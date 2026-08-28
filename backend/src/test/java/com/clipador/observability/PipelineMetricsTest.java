package com.clipador.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.clipador.job.domain.JobStatus;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.video.domain.Video;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PipelineMetricsTest {
    @Test
    void recordsLifecycleOutcomesAndEndToEndDuration() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMetrics metrics = new PipelineMetrics(registry);
        ProcessingJob job = ProcessingJob.received(
                Video.upload("video.mp4", "Video"), "idempotency-key", "correlation-id");
        Instant started = Instant.parse("2026-08-27T12:00:00Z");

        metrics.recordRegistration(job, true);
        job.transitionTo(JobStatus.DOWNLOADING, 5, started);
        metrics.recordTransition(job, JobStatus.RECEIVED, JobStatus.DOWNLOADING, started);
        job.fail("TEST_FAILURE", "failure", started.plusSeconds(12));
        metrics.recordTransition(job, JobStatus.DOWNLOADING, JobStatus.FAILED, started.plusSeconds(12));

        assertThat(registry.get("clipador.jobs").tag("outcome", "created").counter().count()).isEqualTo(1);
        assertThat(registry.get("clipador.jobs").tag("outcome", "failed").counter().count()).isEqualTo(1);
        assertThat(registry.get("clipador.job.processing.duration").tag("outcome", "failed")
                .timer().totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(12);
    }

    @Test
    void recordsSuccessfulAndFailedRenderOutputsSeparately() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMetrics metrics = new PipelineMetrics(registry);

        metrics.recordRenders(3, 1);

        assertThat(registry.get("clipador.clips.generated").counter().count()).isEqualTo(3);
        assertThat(registry.get("clipador.clips.failed").counter().count()).isEqualTo(1);
    }
}
