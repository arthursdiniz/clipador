package com.clipador.observability;

import com.clipador.job.domain.JobStatus;
import com.clipador.job.domain.ProcessingJob;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class PipelineMetrics {
    private static final Logger log = LoggerFactory.getLogger(PipelineMetrics.class);
    private final MeterRegistry registry;

    public PipelineMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRegistration(ProcessingJob job, boolean created) {
        Counter.builder("clipador.jobs")
                .description("Processing job registration outcomes")
                .tag("outcome", created ? "created" : "duplicate")
                .tag("source", job.getVideo().getSourceType().name().toLowerCase())
                .register(registry)
                .increment();
    }

    public void recordRetry(ProcessingJob job) {
        jobCounter("retried", job).increment();
    }

    public void recordTransition(ProcessingJob job, JobStatus previous, JobStatus target, Instant now) {
        Counter.builder("clipador.job.transitions")
                .description("Persisted processing job state transitions")
                .tag("from", previous == null ? "none" : previous.name().toLowerCase())
                .tag("to", target.name().toLowerCase())
                .register(registry)
                .increment();

        try (MDC.MDCCloseable ignoredJob = MDC.putCloseable("jobId", job.getId().toString());
             MDC.MDCCloseable ignoredVideo = MDC.putCloseable("videoId", job.getVideo().getId().toString());
             MDC.MDCCloseable ignoredCorrelation = MDC.putCloseable("correlationId", job.getCorrelationId())) {
            log.info("Pipeline transitioned from={} to={} progress={}", previous, target, job.getProgress());
        }

        if (!target.isTerminal()) return;
        jobCounter(target.name().toLowerCase(), job).increment();
        Instant started = job.getStartedAt();
        Instant completed = job.getCompletedAt() == null ? now : job.getCompletedAt();
        if (started != null && !completed.isBefore(started)) {
            Timer.builder("clipador.job.processing.duration")
                    .description("End-to-end processing job duration")
                    .tag("outcome", target.name().toLowerCase())
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(Duration.between(started, completed));
        }
    }

    public void recordWorkerResult(String taskType, String outcome) {
        Counter.builder("clipador.worker.tasks")
                .description("Media worker task results applied by the backend")
                .tag("task", taskType.toLowerCase())
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void recordRenders(int succeeded, int failed) {
        if (succeeded > 0) {
            Counter.builder("clipador.clips.generated")
                    .description("Successfully generated clip outputs")
                    .register(registry)
                    .increment(succeeded);
        }
        if (failed > 0) {
            Counter.builder("clipador.clips.failed")
                    .description("Failed clip outputs isolated from successful renders")
                    .register(registry)
                    .increment(failed);
        }
    }

    private Counter jobCounter(String outcome, ProcessingJob job) {
        return Counter.builder("clipador.jobs")
                .description("Processing job lifecycle outcomes")
                .tag("outcome", outcome)
                .tag("source", job.getVideo().getSourceType().name().toLowerCase())
                .register(registry);
    }
}
