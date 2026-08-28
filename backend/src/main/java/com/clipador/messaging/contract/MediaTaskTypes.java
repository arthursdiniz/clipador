package com.clipador.messaging.contract;

import java.util.Set;

public final class MediaTaskTypes {
    public static final String VALIDATE_MEDIA = "VALIDATE_MEDIA";
    public static final String EXTRACT_AUDIO = "EXTRACT_AUDIO";
    public static final String TRANSCRIBE_AUDIO = "TRANSCRIBE_AUDIO";
    public static final String ANALYZE_CONTENT = "ANALYZE_CONTENT";
    public static final String RENDER_CLIPS = "RENDER_CLIPS";
    private static final Set<String> SUPPORTED = Set.of(
            VALIDATE_MEDIA, EXTRACT_AUDIO, TRANSCRIBE_AUDIO, ANALYZE_CONTENT, RENDER_CLIPS);

    private MediaTaskTypes() {}

    public static boolean isSupported(String taskType) {
        return SUPPORTED.contains(taskType);
    }
}
