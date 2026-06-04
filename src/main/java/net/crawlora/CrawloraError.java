package net.crawlora;

import java.util.Collections;
import java.util.Map;

/**
 * Base class for every error raised by the SDK. Carries the HTTP status, the
 * parsed API {@code code}/body, the raw response text, response headers, and the
 * request id (when request-id tracking is enabled).
 */
public class CrawloraError extends RuntimeException {
    private final int status;
    private final Integer code;
    private final Object body;
    private final String rawBody;
    private final Map<String, String> headers;
    private final String requestId;

    public CrawloraError(
            String message,
            int status,
            Integer code,
            Object body,
            String rawBody,
            Map<String, String> headers,
            String requestId,
            Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.body = body;
        this.rawBody = rawBody == null ? "" : rawBody;
        this.headers = headers == null ? Collections.emptyMap() : Map.copyOf(headers);
        this.requestId = requestId;
    }

    /** HTTP status code, or {@code 0} for transport/network failures. */
    public int getStatus() {
        return status;
    }

    /** The {@code code} field from a JSON error envelope, if present. */
    public Integer getCode() {
        return code;
    }

    /** The parsed response body (Map/List/String/Number/Boolean), if any. */
    public Object getBody() {
        return body;
    }

    /** The raw, undecoded response text. */
    public String getRawBody() {
        return rawBody;
    }

    /** Response headers (lower/mixed case as received). */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /** The request id, when request-id tracking is enabled. */
    public String getRequestId() {
        return requestId;
    }

    /** Maps an HTTP status to the matching error class. */
    public static Class<? extends CrawloraError> errorClassFor(int status) {
        if (status >= 400 && status < 500) {
            return CrawloraClientError.class;
        }
        if (status >= 500) {
            return CrawloraServerError.class;
        }
        return CrawloraError.class;
    }
}
