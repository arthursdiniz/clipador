package com.clipador.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ExternalProcessRunner {
    private static final int MAX_CAPTURED_OUTPUT = 2 * 1024 * 1024;

    public ProcessResult run(List<String> command, Duration timeout, Path workingDirectory) {
        if (command == null || command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("External command contains an invalid argument");
        }
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (workingDirectory != null) builder.directory(workingDirectory.toFile());
            process = builder.start();
        } catch (IOException exception) {
            throw new ExternalProcessException("TOOL_UNAVAILABLE", "Required media tool could not be started", exception);
        }

        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readOutput(process.getInputStream()));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new ExternalProcessException("TOOL_TIMEOUT", "Media tool exceeded its configured timeout");
            }
            String captured = output.join();
            if (process.exitValue() != 0) {
                throw new ExternalProcessException("TOOL_FAILED", safeMessage(captured));
            }
            return new ProcessResult(process.exitValue(), captured);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new ExternalProcessException("TOOL_INTERRUPTED", "Media tool execution was interrupted", exception);
        }
    }

    private String readOutput(InputStream input) {
        try (input) {
            byte[] buffer = new byte[8192];
            byte[] captured = new byte[MAX_CAPTURED_OUTPUT];
            int capturedLength = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                int copy = Math.min(read, MAX_CAPTURED_OUTPUT - capturedLength);
                if (copy > 0) {
                    System.arraycopy(buffer, 0, captured, capturedLength, copy);
                    capturedLength += copy;
                }
            }
            return new String(captured, 0, capturedLength, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ExternalProcessException("TOOL_OUTPUT_FAILED", "Could not read media tool output", exception);
        }
    }

    private String safeMessage(String output) {
        String normalized = output == null ? "" : output.replaceAll("[\\r\\n]+", " ").trim();
        if (normalized.isBlank()) return "Media tool failed without diagnostic output";
        return normalized.substring(0, Math.min(normalized.length(), 1000));
    }

    public record ProcessResult(int exitCode, String output) {}
}

