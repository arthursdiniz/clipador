package com.clipador.transcript.domain;

import com.clipador.job.domain.ProcessingJob;
import com.clipador.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "transcript")
public class Transcript extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private ProcessingJob job;

    @Column(name = "detected_language", nullable = false, length = 20)
    private String detectedLanguage;

    @Column(name = "language_probability", precision = 5, scale = 4)
    private BigDecimal languageProbability;

    @Column(name = "engine", nullable = false, length = 100)
    private String engine;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "word_timestamps", nullable = false)
    private boolean wordTimestamps;

    @Column(name = "full_text", columnDefinition = "text")
    private String fullText;

    protected Transcript() {}

    private Transcript(ProcessingJob job, String detectedLanguage, BigDecimal languageProbability,
                       String engine, String modelName, boolean wordTimestamps, String fullText) {
        this.job = job;
        this.detectedLanguage = required(detectedLanguage, "detectedLanguage", 20);
        this.languageProbability = languageProbability;
        this.engine = required(engine, "engine", 100);
        this.modelName = truncate(modelName, 100);
        this.wordTimestamps = wordTimestamps;
        this.fullText = fullText;
    }

    public static Transcript imported(ProcessingJob job, String detectedLanguage,
                                      BigDecimal languageProbability, String engine,
                                      String modelName, boolean wordTimestamps, String fullText) {
        if (job == null) throw new IllegalArgumentException("job is required");
        if (languageProbability != null
                && (languageProbability.signum() < 0 || languageProbability.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("languageProbability must be between 0 and 1");
        }
        return new Transcript(job, detectedLanguage, languageProbability, engine,
                modelName, wordTimestamps, fullText);
    }

    private String required(String value, String name, int limit) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return truncate(value, limit);
    }

    private String truncate(String value, int limit) {
        return value == null ? null : value.substring(0, Math.min(value.length(), limit));
    }

    public ProcessingJob getJob() { return job; }
    public String getDetectedLanguage() { return detectedLanguage; }
    public BigDecimal getLanguageProbability() { return languageProbability; }
    public String getEngine() { return engine; }
    public String getModelName() { return modelName; }
    public boolean isWordTimestamps() { return wordTimestamps; }
    public String getFullText() { return fullText; }
}
