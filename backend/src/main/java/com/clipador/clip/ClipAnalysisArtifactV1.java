package com.clipador.clip;

import java.util.List;
import java.util.UUID;

public record ClipAnalysisArtifactV1(
        int schemaVersion,
        UUID jobId,
        UUID videoId,
        String provider,
        List<Candidate> candidates) {

    public record Candidate(
            String candidateKey,
            double start,
            double end,
            double semanticScore,
            double audioScore,
            double visualScore,
            double narrativeScore,
            double hookScore,
            double contextPenalty,
            double finalScore,
            String reason,
            String hook,
            String category,
            String sourceText) {}
}
