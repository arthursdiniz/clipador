package com.clipador.messaging;

import static com.clipador.messaging.RabbitTopology.*;

import java.util.Map;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {

    @Bean
    RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean DirectExchange commandExchange() { return new DirectExchange(COMMAND_EXCHANGE, true, false); }
    @Bean DirectExchange resultExchange() { return new DirectExchange(RESULT_EXCHANGE, true, false); }
    @Bean DirectExchange retryExchange() { return new DirectExchange(RETRY_EXCHANGE, true, false); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false); }

    @Bean Queue mediaValidateQueue() {
        return quorumQueue(MEDIA_VALIDATE_QUEUE, MEDIA_VALIDATE_DEAD_KEY);
    }

    @Bean Queue mediaValidateRetryQueue(MessagingProperties properties) {
        return retryQueue(MEDIA_VALIDATE_RETRY_QUEUE, properties.retryDelay().toMillis(),
                COMMAND_EXCHANGE, MEDIA_VALIDATE_KEY);
    }

    @Bean Queue mediaValidateDeadQueue() { return durableQueue(MEDIA_VALIDATE_DEAD_QUEUE); }

    @Bean Queue mediaExtractAudioQueue() {
        return quorumQueue(MEDIA_EXTRACT_AUDIO_QUEUE, MEDIA_EXTRACT_AUDIO_DEAD_KEY);
    }

    @Bean Queue mediaExtractAudioRetryQueue(MessagingProperties properties) {
        return retryQueue(MEDIA_EXTRACT_AUDIO_RETRY_QUEUE, properties.retryDelay().toMillis(),
                COMMAND_EXCHANGE, MEDIA_EXTRACT_AUDIO_KEY);
    }

    @Bean Queue mediaExtractAudioDeadQueue() { return durableQueue(MEDIA_EXTRACT_AUDIO_DEAD_QUEUE); }

    @Bean Queue mediaTranscribeQueue() {
        return quorumQueue(MEDIA_TRANSCRIBE_QUEUE, MEDIA_TRANSCRIBE_DEAD_KEY);
    }

    @Bean Queue mediaTranscribeRetryQueue(MessagingProperties properties) {
        return retryQueue(MEDIA_TRANSCRIBE_RETRY_QUEUE, properties.retryDelay().toMillis(),
                COMMAND_EXCHANGE, MEDIA_TRANSCRIBE_KEY);
    }

    @Bean Queue mediaTranscribeDeadQueue() { return durableQueue(MEDIA_TRANSCRIBE_DEAD_QUEUE); }

    @Bean Queue mediaAnalyzeQueue() {
        return quorumQueue(MEDIA_ANALYZE_QUEUE, MEDIA_ANALYZE_DEAD_KEY);
    }

    @Bean Queue mediaAnalyzeRetryQueue(MessagingProperties properties) {
        return retryQueue(MEDIA_ANALYZE_RETRY_QUEUE, properties.retryDelay().toMillis(),
                COMMAND_EXCHANGE, MEDIA_ANALYZE_KEY);
    }

    @Bean Queue mediaAnalyzeDeadQueue() { return durableQueue(MEDIA_ANALYZE_DEAD_QUEUE); }

    @Bean Queue mediaRenderQueue() { return quorumQueue(MEDIA_RENDER_QUEUE, MEDIA_RENDER_DEAD_KEY); }

    @Bean Queue mediaRenderRetryQueue(MessagingProperties properties) {
        return retryQueue(MEDIA_RENDER_RETRY_QUEUE, properties.retryDelay().toMillis(),
                COMMAND_EXCHANGE, MEDIA_RENDER_KEY);
    }

    @Bean Queue mediaRenderDeadQueue() { return durableQueue(MEDIA_RENDER_DEAD_QUEUE); }

    @Bean Queue backendResultsQueue() {
        return quorumQueue(BACKEND_RESULTS_QUEUE, MEDIA_RESULT_DEAD_KEY);
    }

    @Bean Queue backendResultsRetryQueue(MessagingProperties properties) {
        return retryQueue(BACKEND_RESULTS_RETRY_QUEUE, properties.retryDelay().toMillis(),
                RESULT_EXCHANGE, MEDIA_RESULT_KEY);
    }

    @Bean Queue backendResultsDeadQueue() { return durableQueue(BACKEND_RESULTS_DEAD_QUEUE); }

    @Bean Binding mediaValidateBinding(Queue mediaValidateQueue, DirectExchange commandExchange) {
        return BindingBuilder.bind(mediaValidateQueue).to(commandExchange).with(MEDIA_VALIDATE_KEY);
    }

    @Bean Binding mediaValidateRetryBinding(Queue mediaValidateRetryQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(mediaValidateRetryQueue).to(retryExchange).with(MEDIA_VALIDATE_RETRY_KEY);
    }

    @Bean Binding mediaValidateDeadBinding(Queue mediaValidateDeadQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(mediaValidateDeadQueue).to(deadLetterExchange).with(MEDIA_VALIDATE_DEAD_KEY);
    }

    @Bean Binding mediaExtractAudioBinding(Queue mediaExtractAudioQueue, DirectExchange commandExchange) {
        return BindingBuilder.bind(mediaExtractAudioQueue).to(commandExchange).with(MEDIA_EXTRACT_AUDIO_KEY);
    }

    @Bean Binding mediaExtractAudioRetryBinding(Queue mediaExtractAudioRetryQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(mediaExtractAudioRetryQueue).to(retryExchange).with(MEDIA_EXTRACT_AUDIO_RETRY_KEY);
    }

    @Bean Binding mediaExtractAudioDeadBinding(Queue mediaExtractAudioDeadQueue,
                                               DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(mediaExtractAudioDeadQueue).to(deadLetterExchange)
                .with(MEDIA_EXTRACT_AUDIO_DEAD_KEY);
    }

    @Bean Binding mediaTranscribeBinding(Queue mediaTranscribeQueue, DirectExchange commandExchange) {
        return BindingBuilder.bind(mediaTranscribeQueue).to(commandExchange).with(MEDIA_TRANSCRIBE_KEY);
    }

    @Bean Binding mediaTranscribeRetryBinding(Queue mediaTranscribeRetryQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(mediaTranscribeRetryQueue).to(retryExchange).with(MEDIA_TRANSCRIBE_RETRY_KEY);
    }

    @Bean Binding mediaTranscribeDeadBinding(Queue mediaTranscribeDeadQueue,
                                             DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(mediaTranscribeDeadQueue).to(deadLetterExchange)
                .with(MEDIA_TRANSCRIBE_DEAD_KEY);
    }

    @Bean Binding mediaAnalyzeBinding(Queue mediaAnalyzeQueue, DirectExchange commandExchange) {
        return BindingBuilder.bind(mediaAnalyzeQueue).to(commandExchange).with(MEDIA_ANALYZE_KEY);
    }

    @Bean Binding mediaAnalyzeRetryBinding(Queue mediaAnalyzeRetryQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(mediaAnalyzeRetryQueue).to(retryExchange).with(MEDIA_ANALYZE_RETRY_KEY);
    }

    @Bean Binding mediaAnalyzeDeadBinding(Queue mediaAnalyzeDeadQueue,
                                          DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(mediaAnalyzeDeadQueue).to(deadLetterExchange).with(MEDIA_ANALYZE_DEAD_KEY);
    }

    @Bean Binding mediaRenderBinding(Queue mediaRenderQueue, DirectExchange commandExchange) {
        return BindingBuilder.bind(mediaRenderQueue).to(commandExchange).with(MEDIA_RENDER_KEY);
    }

    @Bean Binding mediaRenderRetryBinding(Queue mediaRenderRetryQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(mediaRenderRetryQueue).to(retryExchange).with(MEDIA_RENDER_RETRY_KEY);
    }

    @Bean Binding mediaRenderDeadBinding(Queue mediaRenderDeadQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(mediaRenderDeadQueue).to(deadLetterExchange).with(MEDIA_RENDER_DEAD_KEY);
    }

    @Bean Binding backendResultsBinding(Queue backendResultsQueue, DirectExchange resultExchange) {
        return BindingBuilder.bind(backendResultsQueue).to(resultExchange).with(MEDIA_RESULT_KEY);
    }

    @Bean Binding backendResultsRetryBinding(Queue backendResultsRetryQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(backendResultsRetryQueue).to(retryExchange).with(MEDIA_RESULT_RETRY_KEY);
    }

    @Bean Binding backendResultsDeadBinding(Queue backendResultsDeadQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(backendResultsDeadQueue).to(deadLetterExchange).with(MEDIA_RESULT_DEAD_KEY);
    }

    @Bean
    SimpleRabbitListenerContainerFactory manualAckRabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }

    private Queue quorumQueue(String name, String deadLetterRoutingKey) {
        return QueueBuilder.durable(name)
                .quorum()
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .withArgument("x-overflow", "reject-publish")
                .build();
    }

    private Queue retryQueue(String name, long ttl, String targetExchange, String targetRoutingKey) {
        return new Queue(name, true, false, false, Map.of(
                "x-queue-type", "quorum",
                "x-message-ttl", ttl,
                "x-dead-letter-exchange", targetExchange,
                "x-dead-letter-routing-key", targetRoutingKey,
                "x-dead-letter-strategy", "at-least-once",
                "x-overflow", "reject-publish"));
    }

    private Queue durableQueue(String name) {
        return QueueBuilder.durable(name).quorum().build();
    }
}
