package com.clipador.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("clipador.media-tools")
public record MediaToolsProperties(
        @NotBlank String ytDlpExecutable,
        @NotBlank String ffprobeExecutable) {}

