package com.clipador.transcript;

import java.util.List;
import java.util.UUID;

public record TranscriptArtifactV1(
        int schemaVersion,
        UUID jobId,
        UUID videoId,
        String engine,
        String modelName,
        String detectedLanguage,
        Double languageProbability,
        boolean wordTimestamps,
        Double durationSeconds,
        Double durationAfterVad,
        String fullText,
        List<Segment> segments
) {
    public record Segment(int index, double start, double end, String text,
                          Double confidence, List<Word> words) {}
    public record Word(double start, double end, String word, Double probability) {}
}
