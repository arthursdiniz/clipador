package com.clipador.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final Duration timeout;

    public RabbitPublisher(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.timeout = properties.publishTimeout();
    }

    public void publish(String exchange, String routingKey, UUID messageId, String type,
                        byte[] body, Map<String, Object> headers) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(messageId.toString());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setType(type);
        if (headers != null) properties.getHeaders().putAll(headers);
        CorrelationData correlation = new CorrelationData(messageId.toString());
        rabbitTemplate.send(exchange, routingKey, new Message(body, properties), correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) throw new IllegalStateException("RabbitMQ rejected publish: " + confirm.reason());
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ returned unroutable message: "
                        + correlation.getReturned().getReplyText());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting RabbitMQ confirmation", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("RabbitMQ publish was not confirmed", exception);
        }
    }
}
