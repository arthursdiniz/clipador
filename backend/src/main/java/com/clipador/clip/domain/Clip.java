package com.clipador.clip.domain;

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
import java.math.BigDecimal;

@Entity
@Table(name = "clip")
public class Clip extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private ProcessingJob job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private ClipCandidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ClipFormat format;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "duration_seconds", nullable = false, precision = 12, scale = 3)
    private BigDecimal durationSeconds;

    @Column(name = "storage_path", length = 1024)
    private String storagePath;

    @Column(name = "subtitle_path", length = 1024)
    private String subtitlePath;

    @Column(name = "srt_path", length = 1024)
    private String srtPath;

    @Column(name = "vtt_path", length = 1024)
    private String vttPath;

    @Column(name = "ass_path", length = 1024)
    private String assPath;

    @Column(name = "thumbnail_path", length = 1024)
    private String thumbnailPath;

    @Column(name = "render_error", columnDefinition = "text")
    private String renderError;

    protected Clip() {}

    private Clip(ProcessingJob job, ClipCandidate candidate, ClipFormat format, int width, int height,
                 BigDecimal durationSeconds, String storagePath, String subtitlePath,
                 String srtPath, String vttPath, String assPath,
                 String thumbnailPath, String renderError) {
        if (job == null || candidate == null || candidate.getJob() == null
                || !candidate.getJob().getId().equals(job.getId()) || format == null) {
            throw new IllegalArgumentException("Clip job, candidate and format are required");
        }
        if (width <= 0 || height <= 0 || durationSeconds == null || durationSeconds.signum() <= 0) {
            throw new IllegalArgumentException("Clip dimensions and duration are invalid");
        }
        if ((storagePath == null || storagePath.isBlank()) == (renderError == null || renderError.isBlank())) {
            throw new IllegalArgumentException("Clip must contain either an artifact or a render error");
        }
        this.job = job;
        this.candidate = candidate;
        this.format = format;
        this.width = width;
        this.height = height;
        this.durationSeconds = durationSeconds;
        this.storagePath = clean(storagePath, 1024);
        this.subtitlePath = clean(subtitlePath, 1024);
        this.srtPath = clean(srtPath, 1024);
        this.vttPath = clean(vttPath, 1024);
        this.assPath = clean(assPath, 1024);
        this.thumbnailPath = clean(thumbnailPath, 1024);
        this.renderError = clean(renderError, 4000);
    }

    public static Clip rendered(ProcessingJob job, ClipCandidate candidate, ClipFormat format,
                                int width, int height, BigDecimal durationSeconds,
                                String storagePath, String srtPath, String vttPath,
                                String assPath, String thumbnailPath) {
        return new Clip(job, candidate, format, width, height, durationSeconds,
                storagePath, assPath, srtPath, vttPath, assPath, thumbnailPath, null);
    }

    public static Clip failed(ProcessingJob job, ClipCandidate candidate, ClipFormat format,
                              int width, int height, BigDecimal durationSeconds, String error) {
        return new Clip(job, candidate, format, width, height, durationSeconds,
                null, null, null, null, null, null, error);
    }

    private static String clean(String value, int limit) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.substring(0, Math.min(normalized.length(), limit));
    }

    public ProcessingJob getJob() { return job; }
    public ClipCandidate getCandidate() { return candidate; }
    public ClipFormat getFormat() { return format; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public BigDecimal getDurationSeconds() { return durationSeconds; }
    public String getStoragePath() { return storagePath; }
    public String getSubtitlePath() { return subtitlePath; }
    public String getSrtPath() { return srtPath; }
    public String getVttPath() { return vttPath; }
    public String getAssPath() { return assPath; }
    public String getThumbnailPath() { return thumbnailPath; }
    public String getRenderError() { return renderError; }
}
