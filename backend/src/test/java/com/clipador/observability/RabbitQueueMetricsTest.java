package com.clipador.observability;

import static com.clipador.messaging.RabbitTopology.MEDIA_VALIDATE_QUEUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

class RabbitQueueMetricsTest {
    @Test
    void exposesMessageAndConsumerCountsForKnownQueues() {
        RabbitAdmin admin = mock(RabbitAdmin.class);
        Properties properties = new Properties();
        properties.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 7);
        properties.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 2);
        when(admin.getQueueProperties(anyString())).thenReturn(properties);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RabbitQueueMetrics metrics = new RabbitQueueMetrics(admin, registry);

        metrics.bind();
        metrics.refresh();

        assertThat(registry.get("clipador.queue.size").tag("queue", MEDIA_VALIDATE_QUEUE)
                .gauge().value()).isEqualTo(7);
        assertThat(registry.get("clipador.queue.consumers").tag("queue", MEDIA_VALIDATE_QUEUE)
                .gauge().value()).isEqualTo(2);
    }
}
