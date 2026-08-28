package com.clipador.observability;

import static com.clipador.messaging.RabbitTopology.*;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RabbitQueueMetrics {
    private static final Logger log = LoggerFactory.getLogger(RabbitQueueMetrics.class);
    private static final List<String> QUEUES = List.of(
            MEDIA_VALIDATE_QUEUE, MEDIA_VALIDATE_RETRY_QUEUE, MEDIA_VALIDATE_DEAD_QUEUE,
            MEDIA_EXTRACT_AUDIO_QUEUE, MEDIA_EXTRACT_AUDIO_RETRY_QUEUE, MEDIA_EXTRACT_AUDIO_DEAD_QUEUE,
            MEDIA_TRANSCRIBE_QUEUE, MEDIA_TRANSCRIBE_RETRY_QUEUE, MEDIA_TRANSCRIBE_DEAD_QUEUE,
            MEDIA_ANALYZE_QUEUE, MEDIA_ANALYZE_RETRY_QUEUE, MEDIA_ANALYZE_DEAD_QUEUE,
            MEDIA_RENDER_QUEUE, MEDIA_RENDER_RETRY_QUEUE, MEDIA_RENDER_DEAD_QUEUE,
            BACKEND_RESULTS_QUEUE, BACKEND_RESULTS_RETRY_QUEUE, BACKEND_RESULTS_DEAD_QUEUE);

    private final RabbitAdmin rabbitAdmin;
    private final MeterRegistry registry;
    private final Map<String, AtomicLong> sizes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> consumers = new ConcurrentHashMap<>();

    public RabbitQueueMetrics(RabbitAdmin rabbitAdmin, MeterRegistry registry) {
        this.rabbitAdmin = rabbitAdmin;
        this.registry = registry;
    }

    @PostConstruct
    void bind() {
        for (String queue : QUEUES) {
            AtomicLong size = sizes.computeIfAbsent(queue, ignored -> new AtomicLong(-1));
            AtomicLong consumerCount = consumers.computeIfAbsent(queue, ignored -> new AtomicLong(-1));
            Gauge.builder("clipador.queue.size", size, AtomicLong::get)
                    .description("Messages ready in a Clipador RabbitMQ queue")
                    .tag("queue", queue)
                    .register(registry);
            Gauge.builder("clipador.queue.consumers", consumerCount, AtomicLong::get)
                    .description("Consumers attached to a Clipador RabbitMQ queue")
                    .tag("queue", queue)
                    .register(registry);
        }
    }

    @Scheduled(fixedDelayString = "${clipador.observability.queue-poll-interval:PT15S}",
            initialDelayString = "${clipador.observability.queue-poll-initial-delay:PT5S}")
    public void refresh() {
        for (String queue : QUEUES) {
            try {
                Properties properties = rabbitAdmin.getQueueProperties(queue);
                if (properties == null) {
                    sizes.get(queue).set(-1);
                    consumers.get(queue).set(-1);
                    continue;
                }
                sizes.get(queue).set(number(properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)));
                consumers.get(queue).set(number(properties.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)));
            } catch (RuntimeException exception) {
                sizes.get(queue).set(-1);
                consumers.get(queue).set(-1);
                log.debug("Could not refresh RabbitMQ metric queue={}", queue, exception);
            }
        }
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : -1;
    }
}
