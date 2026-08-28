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
import java.util.Objects;

@Entity
@Table(name = "clip_candidate")
public class ClipCandidate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private ProcessingJob job;

    @Column(name = "analysis_key", length = 64)
    private String analysisKey;

    @Column(name = "start_time", nullable = false, precision = 12, scale = 3)
    private BigDecimal startTime;

    @Column(name = "end_time", nullable = false, precision = 12, scale = 3)
    private BigDecimal endTime;

    @Column(name = "semantic_score", nullable = false, precision = 6, scale = 5)
    private BigDecimal semanticScore;

    @Column(name = "audio_score", nullable = false, precision = 6, scale = 5)
    private BigDecimal audioScore;

    @Column(name = "visual_score", nullable = false, precision = 6, scale = 5)
    private BigDecimal visualScore;

    @Column(name = "narrative_score", nullable = false, precision = 6, scale = 5)
    private BigDecimal narrativeScore;

    @Column(name = "hook_score", nullable = false, precision = 6, scale = 5)
    private BigDecimal hookScore;

    @Column(name = "context_penalty", nullable = false, precision = 6, scale = 5)
    private BigDecimal contextPenalty;

    @Column(name = "final_score", nullable = false, precision = 6, scale = 5)
    private BigDecimal finalScore;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(length = 500)
    private String hook;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ClipCategory category;

    @Column(nullable = false)
    private boolean selected;

    @Column(name = "source_text", columnDefinition = "text")
    private String sourceText;

    protected ClipCandidate() {}

    private ClipCandidate(ProcessingJob job, String analysisKey, BigDecimal startTime, BigDecimal endTime,
                          BigDecimal semanticScore, BigDecimal audioScore, BigDecimal visualScore,
                          BigDecimal narrativeScore, BigDecimal hookScore, BigDecimal contextPenalty,
                          BigDecimal finalScore, String reason, String hook, ClipCategory category,
                          String sourceText, String title) {
        this.job = Objects.requireNonNull(job, "job is required");
        this.analysisKey = requireText(analysisKey, "analysisKey", 64);
        this.startTime = timestamp(startTime, "startTime", true);
        this.endTime = timestamp(endTime, "endTime", false);
        if (this.endTime.compareTo(this.startTime) <= 0) throw new IllegalArgumentException("Candidate window is invalid");
        this.semanticScore = score(semanticScore, "semanticScore");
        this.audioScore = score(audioScore, "audioScore");
        this.visualScore = score(visualScore, "visualScore");
        this.narrativeScore = score(narrativeScore, "narrativeScore");
        this.hookScore = score(hookScore, "hookScore");
        this.contextPenalty = score(contextPenalty, "contextPenalty");
        this.finalScore = score(finalScore, "finalScore");
        this.reason = requireText(reason, "reason", 1000);
        this.hook = hook == null || hook.isBlank() ? null : requireText(hook, "hook", 500);
        this.title = requireText(title, "title", 100);
        this.category = Objects.requireNonNull(category, "category is required");
        this.sourceText = requireText(sourceText, "sourceText", 50_000);
    }

    public static ClipCandidate analyzed(ProcessingJob job, String analysisKey,
                                         BigDecimal startTime, BigDecimal endTime,
                                         BigDecimal semanticScore, BigDecimal audioScore,
                                         BigDecimal visualScore, BigDecimal narrativeScore,
                                         BigDecimal hookScore, BigDecimal contextPenalty,
                                         BigDecimal finalScore, String reason, String hook,
                                         ClipCategory category, String sourceText, String title) {
        return new ClipCandidate(job, analysisKey, startTime, endTime, semanticScore, audioScore,
                visualScore, narrativeScore, hookScore, contextPenalty, finalScore,
                reason, hook, category, sourceText, title);
    }

    public void select() { selected = true; }

    private static BigDecimal score(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }

    private static BigDecimal timestamp(BigDecimal value, String name, boolean zeroAllowed) {
        if (value == null || value.signum() < 0 || (!zeroAllowed && value.signum() == 0)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(name + " is too long");
        return normalized;
    }

    public ProcessingJob getJob() { return job; }
    public String getAnalysisKey() { return analysisKey; }
    public BigDecimal getStartTime() { return startTime; }
    public BigDecimal getEndTime() { return endTime; }
    public BigDecimal getSemanticScore() { return semanticScore; }
    public BigDecimal getAudioScore() { return audioScore; }
    public BigDecimal getVisualScore() { return visualScore; }
    public BigDecimal getNarrativeScore() { return narrativeScore; }
    public BigDecimal getHookScore() { return hookScore; }
    public BigDecimal getContextPenalty() { return contextPenalty; }
    public BigDecimal getFinalScore() { return finalScore; }
    public String getReason() { return reason; }
    public String getHook() { return hook; }
    public String getTitle() { return title; }
    public ClipCategory getCategory() { return category; }
    public boolean isSelected() { return selected; }
    public String getSourceText() { return sourceText; }
}
