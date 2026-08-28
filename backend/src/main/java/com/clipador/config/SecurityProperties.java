package com.clipador.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("clipador.security")
public record SecurityProperties(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._@-]{3,100}") String username,
        @NotBlank @Size(min = 12, max = 200) String password,
        boolean apiDocsPublic) {}
