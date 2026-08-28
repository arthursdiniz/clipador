package com.clipador.job.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class JobStateMachine {

    private static final Map<JobStatus, Set<JobStatus>> TRANSITIONS = transitions();

    private JobStateMachine() {}

    public static boolean canTransition(JobStatus from, JobStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    private static Map<JobStatus, Set<JobStatus>> transitions() {
        EnumMap<JobStatus, Set<JobStatus>> map = new EnumMap<>(JobStatus.class);
        forward(map, JobStatus.RECEIVED, JobStatus.DOWNLOADING);
        forward(map, JobStatus.DOWNLOADING, JobStatus.DOWNLOADED);
        forward(map, JobStatus.DOWNLOADED, JobStatus.EXTRACTING_AUDIO);
        forward(map, JobStatus.EXTRACTING_AUDIO, JobStatus.TRANSCRIBING);
        forward(map, JobStatus.TRANSCRIBING, JobStatus.TRANSCRIBED);
        forward(map, JobStatus.TRANSCRIBED, JobStatus.ANALYZING);
        forward(map, JobStatus.ANALYZING, JobStatus.ANALYZED);
        forward(map, JobStatus.ANALYZED, JobStatus.SELECTING_CLIPS);
        forward(map, JobStatus.SELECTING_CLIPS, JobStatus.GENERATING_CLIPS);
        forward(map, JobStatus.GENERATING_CLIPS, JobStatus.GENERATING_SUBTITLES);
        forward(map, JobStatus.GENERATING_SUBTITLES, JobStatus.RENDERING);
        forward(map, JobStatus.RENDERING, JobStatus.COMPLETED);

        for (JobStatus status : JobStatus.values()) {
            if (!status.isTerminal()) {
                map.computeIfAbsent(status, ignored -> EnumSet.noneOf(JobStatus.class))
                        .addAll(EnumSet.of(JobStatus.FAILED, JobStatus.CANCELLED));
            }
        }
        map.put(JobStatus.FAILED, EnumSet.of(JobStatus.RECEIVED, JobStatus.CANCELLED));
        return Map.copyOf(map);
    }

    private static void forward(Map<JobStatus, Set<JobStatus>> map, JobStatus from, JobStatus to) {
        map.computeIfAbsent(from, ignored -> EnumSet.noneOf(JobStatus.class)).add(to);
    }
}

