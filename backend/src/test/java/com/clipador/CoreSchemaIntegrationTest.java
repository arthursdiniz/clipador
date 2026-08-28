package com.clipador;

import static org.assertj.core.api.Assertions.assertThat;

import com.clipador.video.VideoRepository;
import com.clipador.video.domain.Video;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "clipador.security.username=test-user",
        "clipador.security.password=test-password",
        "spring.rabbitmq.username=test-user",
        "spring.rabbitmq.password=test-password"
})
@Testcontainers(disabledWithoutDocker = true)
class CoreSchemaIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    VideoRepository videos;

    @Test
    void flywaySchemaMatchesJpaMappings() {
        Video saved = videos.saveAndFlush(
                Video.youtube("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "Schema validation"));
        assertThat(videos.findById(saved.getId())).isPresent();
    }
}

