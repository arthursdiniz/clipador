package com.clipador.event.domain;

import com.clipador.job.domain.JobStatus;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processing_event")
public class ProcessingEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private ProcessingJob job;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 40)
    private JobStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 40)
    private JobStatus toStatus;

    @Column(nullable = false)
    private int progress;

    @Column(length = 500)
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ProcessingEvent() {}

    private ProcessingEvent(ProcessingJob job, JobStatus fromStatus, JobStatus toStatus,
                            int progress, String message, Instant occurredAt) {
        this.job = job;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.progress = progress;
        this.message = message == null ? null : message.substring(0, Math.min(message.length(), 500));
        this.occurredAt = occurredAt;
    }

    public static ProcessingEvent of(ProcessingJob job, JobStatus fromStatus, JobStatus toStatus,
                                     int progress, String message, Instant occurredAt) {
        return new ProcessingEvent(job, fromStatus, toStatus, progress, message, occurredAt);
    }
}
