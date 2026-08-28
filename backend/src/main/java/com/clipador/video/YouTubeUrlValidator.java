package com.clipador.video;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class YouTubeUrlValidator {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be");
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    public String normalize(String rawUrl) {
        final URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException | NullPointerException exception) {
            throw new IllegalArgumentException("Invalid YouTube URL");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !ALLOWED_HOSTS.contains(host)
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("Only public HTTPS YouTube URLs are accepted");
        }

        String id = host.equals("youtu.be") ? firstPathSegment(uri.getPath()) : youtubeId(uri);
        if (id == null || !VIDEO_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("YouTube URL does not contain a valid video identifier");
        }
        return "https://www.youtube.com/watch?v=" + id;
    }

    private String youtubeId(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        if ("/watch".equals(path)) {
            return Arrays.stream((uri.getRawQuery() == null ? "" : uri.getRawQuery()).split("&"))
                    .map(value -> value.split("=", 2))
                    .filter(pair -> pair.length == 2 && pair[0].equals("v"))
                    .map(pair -> pair[1])
                    .findFirst().orElse(null);
        }
        if (path.startsWith("/shorts/") || path.startsWith("/embed/") || path.startsWith("/live/")) {
            String[] parts = path.split("/");
            return parts.length >= 3 ? parts[2] : null;
        }
        return null;
    }

    private String firstPathSegment(String path) {
        if (path == null) return null;
        return Arrays.stream(path.split("/")).filter(part -> !part.isBlank()).findFirst().orElse(null);
    }
}
