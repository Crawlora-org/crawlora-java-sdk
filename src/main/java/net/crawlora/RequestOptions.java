package net.crawlora;

import java.util.Map;

/**
 * Per-request overrides for {@link CrawloraClient#request}. All fields are
 * optional; {@code null} means "use the client default".
 */
public final class RequestOptions {
    private String responseType;
    private Double timeout;
    private Map<String, String> headers;
    private Integer retries;
    private RetryPredicate retryPredicate;

    public RequestOptions() {}

    /** Response mode: {@code auto} (default), {@code json}, {@code text}, or {@code stream}. */
    public RequestOptions responseType(String responseType) {
        this.responseType = responseType;
        return this;
    }

    /** Per-request timeout in seconds. */
    public RequestOptions timeout(double timeout) {
        this.timeout = timeout;
        return this;
    }

    /** Extra request headers (override client/auth headers by name). */
    public RequestOptions headers(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    /** Override the client's retry count for this request. */
    public RequestOptions retries(int retries) {
        this.retries = retries;
        return this;
    }

    /** Override the retry decision for this request. */
    public RequestOptions retryPredicate(RetryPredicate retryPredicate) {
        this.retryPredicate = retryPredicate;
        return this;
    }

    public String getResponseType() {
        return responseType;
    }

    public Double getTimeout() {
        return timeout;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Integer getRetries() {
        return retries;
    }

    public RetryPredicate getRetryPredicate() {
        return retryPredicate;
    }
}
