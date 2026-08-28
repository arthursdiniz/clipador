package com.clipador.job.domain;

public enum JobStatus {
    RECEIVED,
    DOWNLOADING,
    DOWNLOADED,
    EXTRACTING_AUDIO,
    TRANSCRIBING,
    TRANSCRIBED,
    ANALYZING,
    ANALYZED,
    SELECTING_CLIPS,
    GENERATING_CLIPS,
    GENERATING_SUBTITLES,
    RENDERING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}

