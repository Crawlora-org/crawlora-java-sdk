package net.crawlora;

import java.util.Map;

/**
 * Pluggable HTTP transport. Implement this to route requests through a custom
 * HTTP stack or a test double. The default is {@link DefaultTransport}, built on
 * {@link java.net.http.HttpClient}.
 */
@FunctionalInterface
public interface Transport {
    /**
     * Perform one HTTP request.
     *
     * @param method  HTTP method (already upper-cased)
     * @param url     fully-built absolute URL including query string
     * @param headers request headers
     * @param body    request body bytes, or {@code null} for no body
     * @param timeout per-request timeout in seconds
     * @return the raw response
     * @throws Exception on any transport failure (mapped to a network error)
     */
    TransportResponse call(String method, String url, Map<String, String> headers, byte[] body, double timeout)
            throws Exception;
}
