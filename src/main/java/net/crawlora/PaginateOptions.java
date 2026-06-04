package net.crawlora;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Options for {@link CrawloraClient#pageIterator} and friends. */
public final class PaginateOptions {
    private String pageParam;
    private String cursorParam;
    private Function<Object, Object> nextCursor;
    private Function<Object, List<Object>> items;
    private Object start;
    private long step = 1;
    private Integer maxPages;
    private String responseType;
    private Double timeout;
    private Map<String, String> headers;

    public PaginateOptions() {}

    /** Force a specific page/offset query parameter (auto-detected otherwise). */
    public PaginateOptions pageParam(String pageParam) {
        this.pageParam = pageParam;
        return this;
    }

    /** Enable cursor pagination on the named query parameter. */
    public PaginateOptions cursorParam(String cursorParam) {
        this.cursorParam = cursorParam;
        return this;
    }

    /** Extract the next cursor from a page (cursor mode). */
    public PaginateOptions nextCursor(Function<Object, Object> nextCursor) {
        this.nextCursor = nextCursor;
        return this;
    }

    /** Extract the item list from a page (item iteration; default: {@code data}). */
    public PaginateOptions items(Function<Object, List<Object>> items) {
        this.items = items;
        return this;
    }

    /** Starting page/offset value or cursor. */
    public PaginateOptions start(Object start) {
        this.start = start;
        return this;
    }

    /** Numeric increment per page (default 1). */
    public PaginateOptions step(long step) {
        this.step = step;
        return this;
    }

    /** Cap the number of pages fetched. */
    public PaginateOptions maxPages(int maxPages) {
        this.maxPages = maxPages;
        return this;
    }

    public PaginateOptions responseType(String responseType) {
        this.responseType = responseType;
        return this;
    }

    public PaginateOptions timeout(double timeout) {
        this.timeout = timeout;
        return this;
    }

    public PaginateOptions headers(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public String getPageParam() {
        return pageParam;
    }

    public String getCursorParam() {
        return cursorParam;
    }

    public Function<Object, Object> getNextCursor() {
        return nextCursor;
    }

    public Function<Object, List<Object>> getItems() {
        return items;
    }

    public Object getStart() {
        return start;
    }

    public long getStep() {
        return step;
    }

    public Integer getMaxPages() {
        return maxPages;
    }

    RequestOptions toRequestOptions() {
        RequestOptions opts = new RequestOptions();
        if (responseType != null) {
            opts.responseType(responseType);
        }
        if (timeout != null) {
            opts.timeout(timeout);
        }
        if (headers != null) {
            opts.headers(headers);
        }
        return opts;
    }
}
