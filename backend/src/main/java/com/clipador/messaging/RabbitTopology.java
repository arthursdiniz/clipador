package com.clipador.messaging;

public final class RabbitTopology {
    public static final String COMMAND_EXCHANGE = "clipador.commands.v1";
    public static final String RESULT_EXCHANGE = "clipador.results.v1";
    public static final String RETRY_EXCHANGE = "clipador.retry.v1";
    public static final String DEAD_LETTER_EXCHANGE = "clipador.dlx.v1";

    public static final String MEDIA_VALIDATE_QUEUE = "clipador.media.validate.v1";
    public static final String MEDIA_VALIDATE_RETRY_QUEUE = "clipador.media.validate.retry.v1";
    public static final String MEDIA_VALIDATE_DEAD_QUEUE = "clipador.media.validate.dlq.v1";
    public static final String MEDIA_EXTRACT_AUDIO_QUEUE = "clipador.media.extract-audio.v1";
    public static final String MEDIA_EXTRACT_AUDIO_RETRY_QUEUE = "clipador.media.extract-audio.retry.v1";
    public static final String MEDIA_EXTRACT_AUDIO_DEAD_QUEUE = "clipador.media.extract-audio.dlq.v1";
    public static final String MEDIA_TRANSCRIBE_QUEUE = "clipador.media.transcribe.v1";
    public static final String MEDIA_TRANSCRIBE_RETRY_QUEUE = "clipador.media.transcribe.retry.v1";
    public static final String MEDIA_TRANSCRIBE_DEAD_QUEUE = "clipador.media.transcribe.dlq.v1";
    public static final String MEDIA_ANALYZE_QUEUE = "clipador.media.analyze.v1";
    public static final String MEDIA_ANALYZE_RETRY_QUEUE = "clipador.media.analyze.retry.v1";
    public static final String MEDIA_ANALYZE_DEAD_QUEUE = "clipador.media.analyze.dlq.v1";
    public static final String MEDIA_RENDER_QUEUE = "clipador.media.render.v1";
    public static final String MEDIA_RENDER_RETRY_QUEUE = "clipador.media.render.retry.v1";
    public static final String MEDIA_RENDER_DEAD_QUEUE = "clipador.media.render.dlq.v1";
    public static final String BACKEND_RESULTS_QUEUE = "clipador.backend.results.v1";
    public static final String BACKEND_RESULTS_RETRY_QUEUE = "clipador.backend.results.retry.v1";
    public static final String BACKEND_RESULTS_DEAD_QUEUE = "clipador.backend.results.dlq.v1";

    public static final String MEDIA_VALIDATE_KEY = "media.validate";
    public static final String MEDIA_VALIDATE_RETRY_KEY = "media.validate.retry";
    public static final String MEDIA_VALIDATE_DEAD_KEY = "media.validate.dead";
    public static final String MEDIA_EXTRACT_AUDIO_KEY = "media.extract-audio";
    public static final String MEDIA_EXTRACT_AUDIO_RETRY_KEY = "media.extract-audio.retry";
    public static final String MEDIA_EXTRACT_AUDIO_DEAD_KEY = "media.extract-audio.dead";
    public static final String MEDIA_TRANSCRIBE_KEY = "media.transcribe";
    public static final String MEDIA_TRANSCRIBE_RETRY_KEY = "media.transcribe.retry";
    public static final String MEDIA_TRANSCRIBE_DEAD_KEY = "media.transcribe.dead";
    public static final String MEDIA_ANALYZE_KEY = "media.analyze";
    public static final String MEDIA_ANALYZE_RETRY_KEY = "media.analyze.retry";
    public static final String MEDIA_ANALYZE_DEAD_KEY = "media.analyze.dead";
    public static final String MEDIA_RENDER_KEY = "media.render";
    public static final String MEDIA_RENDER_RETRY_KEY = "media.render.retry";
    public static final String MEDIA_RENDER_DEAD_KEY = "media.render.dead";
    public static final String MEDIA_RESULT_KEY = "media.result";
    public static final String MEDIA_RESULT_RETRY_KEY = "media.result.retry";
    public static final String MEDIA_RESULT_DEAD_KEY = "media.result.dead";

    private RabbitTopology() {}
}
