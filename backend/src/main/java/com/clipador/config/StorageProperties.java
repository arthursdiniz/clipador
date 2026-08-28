package com.clipador.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("clipador.storage")
public record StorageProperties(@NotBlank String type, @NotNull Path root, @NotNull Path tempRoot) {}
