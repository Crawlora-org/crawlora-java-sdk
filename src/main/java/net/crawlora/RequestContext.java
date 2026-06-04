package net.crawlora;

import java.util.Map;

/**
 * Mutable context handed to {@link BeforeRequest} hooks. Editing {@link #url} or
 * {@link #headers} rewrites the outgoing request.
 */
public final class RequestContext {
    public String operation;
    public String method;
    public String url;
    public Map<String, String> headers;

    public RequestContext(String operation, String method, String url, Map<String, String> headers) {
        this.operation = operation;
        this.method = method;
        this.url = url;
        this.headers = headers;
    }
}
