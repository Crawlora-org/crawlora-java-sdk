package net.crawlora;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link Transport} backed by {@link java.net.http.HttpClient}. The
 * client is reused across calls so HTTP/1.1 keep-alive and HTTP/2 connections
 * are pooled. No third-party HTTP dependency is required.
 */
public final class DefaultTransport implements Transport, AutoCloseable {
    private final HttpClient httpClient;

    public DefaultTransport() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
    }

    public DefaultTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Release the underlying {@link HttpClient} when the runtime supports it
     * (JDK 21+ makes {@code HttpClient} {@link AutoCloseable}); a no-op on JDK 17.
     */
    @Override
    public void close() {
        if (httpClient instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    @Override
    public TransportResponse call(String method, String url, Map<String, String> headers, byte[] body, double timeout)
            throws Exception {
        HttpRequest.BodyPublisher publisher =
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method(method, publisher);
        if (timeout > 0) {
            builder.timeout(Duration.ofMillis((long) (timeout * 1000)));
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            // java.net.http forbids setting these "restricted" headers; it adds
            // them itself, so skip them rather than throwing.
            String lower = entry.getKey().toLowerCase();
            if (lower.equals("content-length") || lower.equals("host") || lower.equals("connection")) {
                continue;
            }
            builder.header(entry.getKey(), entry.getValue());
        }
        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) -> responseHeaders.put(name, String.join(", ", values)));
        return new TransportResponse(response.statusCode(), responseHeaders, response.body());
    }
}
