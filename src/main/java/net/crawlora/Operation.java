package net.crawlora;

import java.util.List;

/**
 * Immutable runtime metadata for a single API operation. Populated by the
 * generated {@link Operations} registry and consumed by {@link CrawloraClient}
 * for request building, validation, and pagination.
 */
public final class Operation {
    public final String id;
    public final String method;
    public final String path;
    public final List<String> pathParams;
    public final List<QueryParam> queryParams;
    public final List<FormParam> formParams;
    public final String bodyParam;
    public final boolean bodyRequired;
    public final List<String> security;
    public final boolean paginatable;
    public final List<String> cursorParams;

    public Operation(
            String id,
            String method,
            String path,
            List<String> pathParams,
            List<QueryParam> queryParams,
            List<FormParam> formParams,
            String bodyParam,
            boolean bodyRequired,
            List<String> security,
            boolean paginatable,
            List<String> cursorParams) {
        this.id = id;
        this.method = method;
        this.path = path;
        this.pathParams = pathParams;
        this.queryParams = queryParams;
        this.formParams = formParams;
        this.bodyParam = bodyParam;
        this.bodyRequired = bodyRequired;
        this.security = security;
        this.paginatable = paginatable;
        this.cursorParams = cursorParams;
    }

    public String getId() {
        return id;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public List<String> getPathParams() {
        return pathParams;
    }

    public List<QueryParam> getQueryParams() {
        return queryParams;
    }

    public List<FormParam> getFormParams() {
        return formParams;
    }

    public String getBodyParam() {
        return bodyParam;
    }

    public boolean isBodyRequired() {
        return bodyRequired;
    }

    public List<String> getSecurity() {
        return security;
    }

    public boolean isPaginatable() {
        return paginatable;
    }

    public List<String> getCursorParams() {
        return cursorParams;
    }
}
