package com.clipador.transcript.domain;

import com.clipador.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transcript_segment")
public class TranscriptSegment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transcript_id", nullable = false)
    private Transcript transcript;

    @Column(name = "segment_index", nullable = false)
    private int segmentIndex;

    @Column(name = "start_time", nullable = false, precision = 12, scale = 3)
    private BigDecimal startTime;

    @Column(name = "end_time", nullable = false, precision = 12, scale = 3)
    private BigDecimal endTime;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "speaker_label", length = 100)
    private String speakerLabel;

    @Column(name = "words_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String wordsJson;

    protected TranscriptSegment() {}

    private TranscriptSegment(Transcript transcript, int segmentIndex, BigDecimal startTime,
                              BigDecimal endTime, String text, BigDecimal confidence,
                              String wordsJson) {
        this.transcript = transcript;
        this.segmentIndex = segmentIndex;
        this.startTime = startTime;
        this.endTime = endTime;
        this.text = text;
        this.confidence = confidence;
        this.wordsJson = wordsJson;
    }

    public static TranscriptSegment imported(Transcript transcript, int segmentIndex,
                                             BigDecimal startTime, BigDecimal endTime,
                                             String text, BigDecimal confidence, String wordsJson) {
        if (transcript == null) throw new IllegalArgumentException("transcript is required");
        if (segmentIndex < 0) throw new IllegalArgumentException("segmentIndex must not be negative");
        if (startTime == null || endTime == null || startTime.signum() < 0
                || endTime.compareTo(startTime) <= 0) {
            throw new IllegalArgumentException("Segment timestamps are invalid");
        }
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Segment text is required");
        if (confidence != null && (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        return new TranscriptSegment(transcript, segmentIndex, startTime, endTime,
                text.trim(), confidence, wordsJson);
    }

    public Transcript getTranscript() { return transcript; }
    public int getSegmentIndex() { return segmentIndex; }
    public BigDecimal getStartTime() { return startTime; }
    public BigDecimal getEndTime() { return endTime; }
    public String getText() { return text; }
    public BigDecimal getConfidence() { return confidence; }
    public String getSpeakerLabel() { return speakerLabel; }
    public String getWordsJson() { return wordsJson; }
}
