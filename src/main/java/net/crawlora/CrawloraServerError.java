package net.crawlora;

import java.util.Map;

/** Raised for 5xx API responses: the API failed to handle a valid request. */
public class CrawloraServerError extends CrawloraError {
    public CrawloraServerError(
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
