package com.clipador.clip;

import com.clipador.clip.domain.ClipCandidate;
import com.clipador.config.ClipAnalysisProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ClipSelectionService {
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "o", "as", "os", "de", "da", "do", "das", "dos", "e", "em", "um", "uma",
            "the", "an", "and", "or", "of", "to", "in", "is", "it", "that", "this");

    private final ClipCandidateRepository candidates;
    private final ClipAnalysisProperties properties;
    private final ClipQuantityPolicy quantityPolicy;

    public ClipSelectionService(ClipCandidateRepository candidates, ClipAnalysisProperties properties,
                                ClipQuantityPolicy quantityPolicy) {
        this.candidates = candidates;
        this.properties = properties;
        this.quantityPolicy = quantityPolicy;
    }

    public List<ClipCandidate> select(com.clipador.job.domain.ProcessingJob job) {
        int targetCount = quantityPolicy.resolve(job.getClipQuantityMode(), job.getRequestedClipCount(),
                job.getVideo().getDurationSeconds());
        job.resolveTargetClipCount(targetCount);
        List<ClipCandidate> selected = new ArrayList<>();
        for (ClipCandidate candidate : candidates.findAllByJobIdOrderByFinalScoreDesc(job.getId())) {
            boolean redundant = selected.stream().anyMatch(chosen -> overlaps(candidate, chosen)
                    || similarity(candidate.getSourceText(), chosen.getSourceText()) >= properties.similarityThreshold());
            if (!redundant) {
                candidate.select();
                selected.add(candidate);
                if (selected.size() == targetCount) break;
            }
        }
        if (selected.isEmpty()) throw new IllegalArgumentException("Analysis produced no selectable clip candidates");
        candidates.saveAll(selected);
        return List.copyOf(selected);
    }

    private boolean overlaps(ClipCandidate left, ClipCandidate right) {
        double start = Math.max(left.getStartTime().doubleValue(), right.getStartTime().doubleValue());
        double end = Math.min(left.getEndTime().doubleValue(), right.getEndTime().doubleValue());
        double intersection = Math.max(0, end - start);
        double shorter = Math.min(duration(left), duration(right));
        return shorter > 0 && intersection / shorter >= properties.overlapThreshold();
    }

    private double duration(ClipCandidate candidate) {
        return candidate.getEndTime().subtract(candidate.getStartTime()).doubleValue();
    }

    private double similarity(String left, String right) {
        Set<String> a = tokens(left);
        Set<String> b = tokens(right);
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private Set<String> tokens(String text) {
        if (text == null) return Set.of();
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> token.length() > 2 && !STOP_WORDS.contains(token))
                .collect(Collectors.toSet());
    }
}
