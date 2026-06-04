package net.crawlora;

import java.util.Map;

/** Raised for 4xx API responses: the request was rejected by the API. */
public class CrawloraClientError extends CrawloraError {
    public CrawloraClientError(
            String message,
            int status,
            Integer code,
            Object body,
            String rawBody,
            Map<String, String> headers,
            String requestId,
            Throwable cause) {
        super(message, status, code, body, rawBody, headers, requestId, cause);
    }
}
