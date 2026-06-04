package net.crawlora;

import java.util.Map;

/**
 * Runs after a successful response is parsed. Returning a non-null value
 * replaces the response body; returning {@code null} keeps it unchanged.
 */
@FunctionalInterface
public interface AfterResponse {
    Object apply(String operationId, int status, Map<String, String> headers, Object body);
}
