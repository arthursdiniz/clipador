package com.clipador.messaging.result;

import com.clipador.messaging.MessagingProperties;
import com.clipador.messaging.RabbitPublisher;
import com.clipador.messaging.RabbitTopology;
import com.clipador.messaging.contract.MediaTaskResultV1;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MediaResultListener {
    private static final Logger log = LoggerFactory.getLogger(MediaResultListener.class);
    private static final String RETRY_HEADER = "x-clipador-retry-count";

    private final ObjectMapper objectMapper;
    private final MediaResultService service;
    private final RabbitPublisher publisher;
    private final int maxRetries;
    private final Counter processed;
    private final Counter deadLettered;

    public MediaResultListener(ObjectMapper objectMapper, MediaResultService service,
                               RabbitPublisher publisher, MessagingProperties properties,
                               MeterRegistry registry) {
        this.objectMapper = objectMapper;
        this.service = service;
        this.publisher = publisher;
        this.maxRetries = properties.resultMaxRetries();
        this.processed = Counter.builder("clipador.worker.results.processed").register(registry);
        this.deadLettered = Counter.builder("clipador.worker.results.dead_lettered").register(registry);
    }

    @RabbitListener(queues = RabbitTopology.BACKEND_RESULTS_QUEUE,
            containerFactory = "manualAckRabbitListenerContainerFactory")
    public void receive(Message message, Channel channel) throws java.io.IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            MediaTaskResultV1 result = objectMapper.readValue(payload, MediaTaskResultV1.class);
            service.process(result, payload);
            channel.basicAck(deliveryTag, false);
            processed.increment();
        } catch (JacksonException | IllegalArgumentException exception) {
            log.error("Rejecting invalid media worker result messageId={}",
                    message.getMessageProperties().getMessageId(), exception);
            channel.basicReject(deliveryTag, false);
            deadLettered.increment();
        } catch (RuntimeException exception) {
            retryOrDeadLetter(message, channel, deliveryTag, exception);
        }
    }

    private void retryOrDeadLetter(Message message, Channel channel, long deliveryTag,
                                   RuntimeException exception) throws java.io.IOException {
        int retries = headerAsInt(message, RETRY_HEADER);
        if (retries >= maxRetries) {
            log.error("Media result exhausted retries messageId={} retries={}",
                    message.getMessageProperties().getMessageId(), retries, exception);
            try {
                String payload = new String(message.getBody(), StandardCharsets.UTF_8);
                MediaTaskResultV1 result = objectMapper.readValue(payload, MediaTaskResultV1.class);
                service.failUnprocessable(result, payload, exception.getMessage());
            } catch (RuntimeException failureRecordingError) {
                log.error("Could not mark job failed after exhausted result processing", failureRecordingError);
            }
            channel.basicReject(deliveryTag, false);
            deadLettered.increment();
            return;
        }
        try {
            Map<String, Object> headers = new HashMap<>(message.getMessageProperties().getHeaders());
            headers.put(RETRY_HEADER, retries + 1);
            String rawId = message.getMessageProperties().getMessageId();
            UUID messageId = rawId == null ? UUID.randomUUID() : UUID.fromString(rawId);
            publisher.publish(RabbitTopology.RETRY_EXCHANGE, RabbitTopology.MEDIA_RESULT_RETRY_KEY,
                    messageId, message.getMessageProperties().getType(), message.getBody(), headers);
            channel.basicAck(deliveryTag, false);
            log.warn("Media result scheduled for retry messageId={} retry={}", messageId, retries + 1, exception);
        } catch (RuntimeException publishFailure) {
            log.error("Could not publish media result retry; original will be redelivered", publishFailure);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private int headerAsInt(Message message, String name) {
        Object value = message.getMessageProperties().getHeader(name);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try { return Integer.parseInt(text); } catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }
}
