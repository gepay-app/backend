package com.gepe.gepay.donation.internal.util;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validasi & normalisasi URL video YouTube. Hanya host YouTube yang diterima;
 * id video diekstrak (11 karakter) dan URL kanonik dibentuk kembali.
 *
 * <p>Bentuk yang didukung: {@code youtube.com/watch?v=ID}, {@code youtu.be/ID},
 * {@code youtube.com/embed/ID}, {@code youtube.com/shorts/ID}, {@code /live/ID},
 * termasuk subdomain {@code www.}, {@code m.}, {@code music.}.
 */
public final class YouTubeUrlParser {

    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    private YouTubeUrlParser() {
    }

    public record Result(String videoId, String canonicalUrl) {
    }

    public static Optional<Result> parse(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null) {
                return Optional.empty();
            }
            host = host.toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();

            String id;
            if (host.equals("youtu.be")) {
                id = firstSegment(path);
            } else if (host.equals("youtube.com")
                    || host.equals("www.youtube.com")
                    || host.equals("m.youtube.com")
                    || host.equals("music.youtube.com")) {
                id = fromYoutubeCom(uri, path);
            } else {
                return Optional.empty();
            }

            if (id == null || !VIDEO_ID.matcher(id).matches()) {
                return Optional.empty();
            }
            return Optional.of(new Result(id, "https://www.youtube.com/watch?v=" + id));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String fromYoutubeCom(URI uri, String path) {
        if (path.equals("/watch")) {
            return queryParam(uri.getQuery(), "v");
        }
        for (String prefix : new String[]{"/embed/", "/shorts/", "/live/", "/v/"}) {
            if (path.startsWith(prefix)) {
                return firstSegment(path.substring(prefix.length()));
            }
        }
        return null;
    }

    private static String firstSegment(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String p = path.startsWith("/") ? path.substring(1) : path;
        int slash = p.indexOf('/');
        return slash >= 0 ? p.substring(0, slash) : p;
    }

    private static String queryParam(String query, String name) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
