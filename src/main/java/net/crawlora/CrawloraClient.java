package net.crawlora;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.crawlora.groups.*;

/**
 * Synchronous client for the Crawlora API.
 *
 * <p>Call operations via grouped helpers ({@code client.bing().search(params)})
 * or dynamically ({@code client.request("bing-search", params, null)}). Supports
 * configurable retries with exponential backoff, jitter, and {@code Retry-After}
 * handling; an {@code onRetry} hook; opt-in request-id and idempotency keys;
 * {@code beforeRequest}/{@code afterResponse} middleware; client-side rate
 * limiting and max concurrency; pagination; and {@code auto}/{@code json}/
 * {@code text}/{@code stream} response modes.
 *
 * <p>Construct via {@link #builder()}.
 */
public final class CrawloraClient extends ClientGroups implements AutoCloseable {
    public static final String DEFAULT_BASE_URL = "https://api.crawlora.net/api/v1";
    public static final double DEFAULT_MAX_RETRY_DELAY = 30.0;
    public static final Set<Integer> DEFAULT_RETRY_STATUSES = Set.of(408, 409, 425, 429);
    public static final String DEFAULT_USER_AGENT = "crawlora-java-sdk/" + Version.VERSION;
    private static final List<String> RESPONSE_TYPES = List.of("auto", "json", "text", "stream");

    private final String apiKey;
    private final String jwtToken;
    private final String baseUrl;
    private final double timeout;
    private final int retries;
    private final double retryDelay;
    private final double maxRetryDelay;
    private final Set<Integer> retryStatuses;
    private final RetryPredicate retryPredicate;
    private final RetryHook onRetry;
    private final boolean requestId;
    private final boolean idempotencyKeys;
    private final RateLimiter rateLimiter;
    private final Logger logger;
    private final List<BeforeRequest> beforeRequest;
    private final List<AfterResponse> afterResponse;
    private final Map<String, String> headers;
    private final String userAgent;
    private final Transport transport;
    private final Map<String, OperationGroup> groupRegistry = new ConcurrentHashMap<>();

    private CrawloraClient(Builder b) {
        this.apiKey = orElse(b.apiKey, env("CRAWLORA_API_KEY", ""));
        this.jwtToken = b.jwtToken == null ? "" : b.jwtToken;
        String base = b.baseUrl != null ? b.baseUrl : env("CRAWLORA_BASE_URL", DEFAULT_BASE_URL);
        this.baseUrl = stripTrailingSlash(base);
        this.timeout = b.timeout;
        this.retries = Math.max(0, b.retries);
        this.retryDelay = Math.max(0.0, b.retryDelay);
        this.maxRetryDelay = Math.max(0.0, b.maxRetryDelay);
        this.retryStatuses = b.retryStatuses == null ? null : Set.copyOf(b.retryStatuses);
        this.retryPredicate = b.retryPredicate;
        this.onRetry = b.onRetry;
        this.requestId = b.requestId;
        this.idempotencyKeys = b.idempotencyKeys;
        this.rateLimiter = (b.rateLimit != null || b.maxConcurrency != null)
                ? new RateLimiter(b.rateLimit, b.maxConcurrency) : null;
        this.logger = b.logger;
        this.beforeRequest = List.copyOf(b.beforeRequest);
        this.afterResponse = List.copyOf(b.afterResponse);
        this.headers = b.headers == null ? Map.of() : new LinkedHashMap<>(b.headers);
        this.userAgent = b.userAgent == null ? "" : b.userAgent;
        this.transport = b.transport != null ? b.transport : new DefaultTransport();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    private static String orElse(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ---- configuration accessors ------------------------------------------

    public String getApiKey() {
        return apiKey;
    }

    public String getJwtToken() {
        return jwtToken;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getUserAgent() {
        return userAgent;
    }

    // ---- group accessors ---------------------------------------------------

    private OperationGroup group(String name) {
        return groupRegistry.computeIfAbsent(name, n -> new OperationGroup(this, Operations.GROUPS.get(n)));
    }

    // Typed per-group accessors (client.bing(), client.google(), …) are inherited
    // from the generated ClientGroups base class.

    /** Generic accessor: returns a dynamic group backed by the named group's operations. */
    public OperationGroup groupOf(String name) {
        if (!Operations.GROUPS.containsKey(name)) {
            throw new IllegalArgumentException("unknown Crawlora group: " + name);
        }
        return group(name);
    }

    /**
     * Release any resources held by the transport (e.g. a pooled HTTP client),
     * enabling try-with-resources. The default transport has nothing to close on
     * JDK 17; custom transports that hold resources are closed if they are
     * {@link AutoCloseable}.
     */
    @Override
    public void close() {
        if (transport instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    // ---- dynamic dispatch --------------------------------------------------

    public Object operation(String operationId, Map<String, Object> params, RequestOptions options) {
        return request(operationId, params, options);
    }

    public Object request(String operationId, Map<String, Object> params, RequestOptions options) {
        Operation operation = Operations.OPERATIONS.get(operationId);
        if (operation == null) {
            throw new IllegalArgumentException("unknown Crawlora operation: " + operationId);
        }
        RequestOptions opts = options == null ? new RequestOptions() : options;
        String responseType = validateResponseType(opts.getResponseType() == null ? "auto" : opts.getResponseType());
        log(Map.of("event", "request", "operation", operationId));
        int maxRetries = opts.getRetries() == null ? retries : Math.max(0, opts.getRetries());
        RetryPredicate localPredicate = opts.getRetryPredicate();
        String idempotencyKey = null;
        if (idempotencyKeys && (operation.method.equals("POST") || operation.method.equals("PATCH"))) {
            idempotencyKey = UUID.randomUUID().toString().replace("-", "");
        }

        Map<String, Object> stringified = stringifyKeys(params);
        int attempt = 0;
        while (true) {
            try {
                return send(operation, new LinkedHashMap<>(stringified), responseType,
                        opts.getTimeout(), opts.getHeaders(), idempotencyKey);
            } catch (CrawloraError exc) {
                boolean retryable = localPredicate != null
                        ? localPredicate.shouldRetry(exc.getStatus(), exc)
                        : isRetryable(exc.getStatus(), exc);
                if (attempt >= maxRetries || !retryable) {
                    throw exc;
                }
                attempt++;
                double delay = computeRetryDelay(attempt, exc.getHeaders());
                log(Map.of("event", "retry", "operation", operationId, "attempt", attempt,
                        "status", exc.getStatus(), "delay", delay));
                if (onRetry != null) {
                    onRetry.onRetry(attempt, exc, delay);
                }
                if (delay > 0) {
                    try {
                        Thread.sleep((long) (delay * 1000));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new CrawloraNetworkError("Crawlora retry interrupted", exc.getRequestId(), ie);
                    }
                }
            }
        }
    }

    private Object send(Operation operation, Map<String, Object> params, String responseType,
                        Double timeoutOverride, Map<String, String> extraHeaders, String idempotencyKey) {
        BuiltRequest built = buildRequest(baseUrl, operation, params);
        Map<String, String> requestHeaders = mergeHeaders(
                headers,
                authHeaders(operation.security, apiKey, jwtToken),
                userAgent.isEmpty() ? Map.of() : Map.of("User-Agent", userAgent),
                built.bodyHeaders,
                extraHeaders == null ? Map.of() : extraHeaders);
        String reqId;
        if (requestId) {
            reqId = ensureRequestId(requestHeaders);
        } else {
            String existing = headerValue(requestHeaders, "x-request-id");
            reqId = existing.isEmpty() ? null : existing;
        }
        if (idempotencyKey != null && headerValue(requestHeaders, "idempotency-key").isEmpty()) {
            requestHeaders.put("Idempotency-Key", idempotencyKey);
        }
        String url = built.url;
        if (!beforeRequest.isEmpty()) {
            RequestContext ctx = new RequestContext(operation.id, operation.method, url, requestHeaders);
            for (BeforeRequest hook : beforeRequest) {
                hook.apply(ctx);
            }
            url = ctx.url;
            requestHeaders = ctx.headers;
        }

        double requestTimeout = timeoutOverride != null ? timeoutOverride : timeout;
        TransportResponse response;
        try {
            if (rateLimiter != null) {
                rateLimiter.acquire();
                try {
                    response = transport.call(operation.method, url, requestHeaders, built.body, requestTimeout);
                } finally {
                    rateLimiter.release();
                }
            } else {
                response = transport.call(operation.method, url, requestHeaders, built.body, requestTimeout);
            }
        } catch (Exception exc) {
            String message = isTimeoutError(exc) ? "Crawlora request timed out" : "Crawlora transport error";
            throw new CrawloraNetworkError(message, reqId, exc);
        }

        String rawBody = new String(response.body, StandardCharsets.UTF_8);
        boolean isError = response.status < 200 || response.status >= 300;
        if (responseType.equals("stream") && !isError) {
            return new ByteArrayInputStream(response.body);
        }

        String parseMode = responseType.equals("stream") ? "auto" : responseType;
        Object parsed;
        try {
            parsed = parseResponse(rawBody, headerValue(response.headers, "content-type"), parseMode);
        } catch (Json.ParseException exc) {
            throw new CrawloraError("Crawlora JSON parse error", response.status, null, null,
                    rawBody, response.headers, reqId, exc);
        }

        if (isError) {
            Integer code = null;
            String message = "HTTP " + response.status;
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) parsed;
                Object codeValue = map.get("code");
                if (codeValue instanceof Number) {
                    code = ((Number) codeValue).intValue();
                }
                Object msg = map.get("msg");
                if (msg != null && !msg.toString().isEmpty()) {
                    message = msg.toString();
                }
            }
            throw newApiError(response.status, message, code, parsed, rawBody, response.headers, reqId);
        }

        for (AfterResponse hook : afterResponse) {
            Object result = hook.apply(operation.id, response.status, response.headers, parsed);
            if (result != null) {
                parsed = result;
            }
        }
        return parsed;
    }

    private CrawloraError newApiError(int status, String message, Integer code, Object body,
                                      String rawBody, Map<String, String> headers, String requestId) {
        if (status >= 400 && status < 500) {
            return new CrawloraClientError(message, status, code, body, rawBody, headers, requestId, null);
        }
        if (status >= 500) {
            return new CrawloraServerError(message, status, code, body, rawBody, headers, requestId, null);
        }
        return new CrawloraError(message, status, code, body, rawBody, headers, requestId, null);
    }

    // ---- retry policy ------------------------------------------------------

    private boolean isRetryable(int status, CrawloraError exc) {
        if (retryPredicate != null) {
            return retryPredicate.shouldRetry(status, exc);
        }
        if (retryStatuses != null) {
            return status == 0 || retryStatuses.contains(status);
        }
        return status == 0 || DEFAULT_RETRY_STATUSES.contains(status) || status >= 500;
    }

    private double computeRetryDelay(int attempt, Map<String, String> headers) {
        Double retryAfter = retryAfterDelay(headers, maxRetryDelay);
        if (retryAfter != null) {
            return retryAfter;
        }
        if (retryDelay <= 0) {
            return 0.0;
        }
        double delay = retryDelay * Math.pow(2, Math.max(0, attempt - 1));
        double jitter = Math.random() * (retryDelay / 2);
        return delay + jitter;
    }

    private void log(Map<String, Object> event) {
        if (logger != null) {
            logger.log(event);
        }
    }

    // ---- pagination --------------------------------------------------------

    /** Eagerly collects every page into a list. See {@link #pageIterator}. */
    public List<Object> paginate(String operationId, Map<String, Object> params, PaginateOptions options) {
        List<Object> pages = new ArrayList<>();
        pageIterator(operationId, params, options).forEachRemaining(pages::add);
        return pages;
    }

    /** A lazy {@link Stream} of pages. */
    public Stream<Object> paginateStream(String operationId, Map<String, Object> params, PaginateOptions options) {
        Iterator<Object> it = pageIterator(operationId, params, options);
        return StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(it, 0), false);
    }

    /**
     * A lazy iterator over successive pages.
     *
     * <p>Numeric mode (default) advances the {@code page}/{@code offset} query
     * parameter and stops on an empty page. Cursor mode (set both
     * {@code cursorParam} and {@code nextCursor}) sends the cursor parameter and
     * stops when {@code nextCursor} returns a falsy value.
     */
    public Iterator<Object> pageIterator(String operationId, Map<String, Object> params, PaginateOptions options) {
        Operation operation = Operations.OPERATIONS.get(operationId);
        if (operation == null) {
            throw new IllegalArgumentException("unknown Crawlora operation: " + operationId);
        }
        PaginateOptions opts = options == null ? new PaginateOptions() : options;
        Map<String, Object> baseParams = stringifyKeys(params);

        if (opts.getCursorParam() != null || opts.getNextCursor() != null) {
            if (opts.getCursorParam() == null || opts.getNextCursor() == null) {
                throw new IllegalArgumentException("cursor pagination requires both cursorParam and nextCursor");
            }
            boolean known = operation.queryParams.stream().anyMatch(p -> p.name.equals(opts.getCursorParam()));
            if (!known) {
                throw new IllegalArgumentException("cursorParam " + opts.getCursorParam()
                        + " is not a query parameter of operation " + operationId);
            }
            return new CursorIterator(operationId, baseParams, opts);
        }

        String pageParam = opts.getPageParam() != null ? opts.getPageParam() : Pagination.detectPageParam(operation);
        if (pageParam == null) {
            throw new IllegalArgumentException(
                    "operation " + operationId + " has no page or offset query parameter to paginate");
        }
        return new NumericIterator(operationId, baseParams, pageParam, opts);
    }

    /** Eagerly collects items across pages using the configured extractor. */
    public List<Object> paginateItems(String operationId, Map<String, Object> params, PaginateOptions options) {
        List<Object> out = new ArrayList<>();
        itemIterator(operationId, params, options).forEachRemaining(out::add);
        return out;
    }

    /** A lazy iterator over individual items across pages. */
    public Iterator<Object> itemIterator(String operationId, Map<String, Object> params, PaginateOptions options) {
        PaginateOptions opts = options == null ? new PaginateOptions() : options;
        java.util.function.Function<Object, List<Object>> extract =
                opts.getItems() != null ? opts.getItems() : Pagination::defaultItems;
        Iterator<Object> pages = pageIterator(operationId, params, opts);
        return new Iterator<>() {
            private Iterator<Object> current = java.util.Collections.emptyIterator();

            private void advance() {
                while (!current.hasNext() && pages.hasNext()) {
                    current = extract.apply(pages.next()).iterator();
                }
            }

            @Override
            public boolean hasNext() {
                advance();
                return current.hasNext();
            }

            @Override
            public Object next() {
                advance();
                return current.next();
            }
        };
    }

    private final class NumericIterator implements Iterator<Object> {
        private final String operationId;
        private final Map<String, Object> baseParams;
        private final String pageParam;
        private final PaginateOptions opts;
        private long pageValue;
        private int fetched = 0;
        private boolean done = false;

        NumericIterator(String operationId, Map<String, Object> baseParams, String pageParam, PaginateOptions opts) {
            this.operationId = operationId;
            this.baseParams = baseParams;
            this.pageParam = pageParam;
            this.opts = opts;
            this.pageValue = opts.getStart() != null
                    ? ((Number) opts.getStart()).longValue() : Pagination.defaultStart(pageParam);
        }

        @Override
        public boolean hasNext() {
            if (done) {
                return false;
            }
            return opts.getMaxPages() == null || fetched < opts.getMaxPages();
        }

        @Override
        public Object next() {
            Map<String, Object> pageParams = new LinkedHashMap<>(baseParams);
            pageParams.put(pageParam, pageValue);
            Object response = request(operationId, pageParams, opts.toRequestOptions());
            fetched++;
            if (Pagination.pageEmpty(response)) {
                done = true;
            } else {
                pageValue += opts.getStep();
            }
            return response;
        }
    }

    private final class CursorIterator implements Iterator<Object> {
        private final String operationId;
        private final Map<String, Object> baseParams;
        private final PaginateOptions opts;
        private Object cursor;
        private int fetched = 0;
        private boolean done = false;

        CursorIterator(String operationId, Map<String, Object> baseParams, PaginateOptions opts) {
            this.operationId = operationId;
            this.baseParams = baseParams;
            this.opts = opts;
            this.cursor = opts.getStart();
        }

        @Override
        public boolean hasNext() {
            if (done) {
                return false;
            }
            return opts.getMaxPages() == null || fetched < opts.getMaxPages();
        }

        @Override
        public Object next() {
            Map<String, Object> pageParams = new LinkedHashMap<>(baseParams);
            if (cursor != null) {
                pageParams.put(opts.getCursorParam(), cursor);
            }
            Object response = request(operationId, pageParams, opts.toRequestOptions());
            fetched++;
            cursor = opts.getNextCursor().apply(response);
            if (isFalsy(cursor)) {
                done = true;
            }
            return response;
        }
    }

    private static boolean isFalsy(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return ((String) value).isEmpty();
        }
        if (value instanceof Boolean) {
            return !((Boolean) value);
        }
        if (value instanceof List) {
            return ((List<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
        }
        return false;
    }

    // ---- request building --------------------------------------------------

    private static final class BuiltRequest {
        final String url;
        final byte[] body;
        final Map<String, String> bodyHeaders;

        BuiltRequest(String url, byte[] body, Map<String, String> bodyHeaders) {
            this.url = url;
            this.body = body;
            this.bodyHeaders = bodyHeaders;
        }
    }

    private BuiltRequest buildRequest(String baseUrl, Operation operation, Map<String, Object> params) {
        validateRequiredParams(operation, params);
        validateEnumParams(operation, params);

        String path = operation.path;
        for (String name : operation.pathParams) {
            Object value = params.get(name);
            if (isMissing(value)) {
                throw new IllegalArgumentException("missing required path parameter: " + name);
            }
            path = path.replace("{" + name + "}", urlEscape(value));
        }

        List<String[]> query = new ArrayList<>();
        for (QueryParam parameter : operation.queryParams) {
            Object value = params.get(parameter.name);
            if (isMissing(value)) {
                continue;
            }
            if (value instanceof Iterable) {
                for (Object item : (Iterable<?>) value) {
                    query.add(new String[] {parameter.name, stringifyParam(item)});
                }
            } else if (value.getClass().isArray()) {
                for (Object item : (Object[]) value) {
                    query.add(new String[] {parameter.name, stringifyParam(item)});
                }
            } else {
                query.add(new String[] {parameter.name, stringifyParam(value)});
            }
        }
        StringBuilder url = new StringBuilder(baseUrl).append(path);
        if (!query.isEmpty()) {
            url.append('?');
            boolean first = true;
            for (String[] pair : query) {
                if (!first) {
                    url.append('&');
                }
                first = false;
                url.append(formEncode(pair[0])).append('=').append(formEncode(pair[1]));
            }
        }

        if (!operation.formParams.isEmpty()) {
            return multipartBody(url.toString(), operation.formParams, params);
        }

        if (operation.bodyParam != null) {
            Object value = params.containsKey(operation.bodyParam) ? params.get(operation.bodyParam) : params.get("body");
            if (value != null) {
                byte[] body = Json.write(value).getBytes(StandardCharsets.UTF_8);
                return new BuiltRequest(url.toString(), body, Map.of("content-type", "application/json"));
            }
        }

        return new BuiltRequest(url.toString(), null, Map.of());
    }

    private void validateRequiredParams(Operation operation, Map<String, Object> params) {
        for (String name : operation.pathParams) {
            if (isMissing(params.get(name))) {
                throw new IllegalArgumentException("missing required path parameter: " + name);
            }
        }
        for (QueryParam parameter : operation.queryParams) {
            if (parameter.required && isMissing(params.get(parameter.name))) {
                throw new IllegalArgumentException("missing required query parameter: " + parameter.name);
            }
        }
        for (FormParam parameter : operation.formParams) {
            if (parameter.required && isMissing(params.get(parameter.name))) {
                throw new IllegalArgumentException("missing required formData parameter: " + parameter.name);
            }
        }
        if (operation.bodyRequired) {
            if (isMissing(params.get(operation.bodyParam)) && isMissing(params.get("body"))) {
                throw new IllegalArgumentException("missing required body parameter: " + operation.bodyParam);
            }
        }
    }

    private void validateEnumParams(Operation operation, Map<String, Object> params) {
        validateEnumList(operation.queryParams.stream()
                .map(p -> new Object[] {p.name, p.enumValues, "query"}).toList(), params);
        validateEnumList(operation.formParams.stream()
                .map(p -> new Object[] {p.name, p.enumValues, "formData"}).toList(), params);
    }

    @SuppressWarnings("unchecked")
    private void validateEnumList(List<Object[]> entries, Map<String, Object> params) {
        for (Object[] entry : entries) {
            String name = (String) entry[0];
            List<String> enumValues = (List<String>) entry[1];
            String location = (String) entry[2];
            Object value = params.get(name);
            if (enumValues.isEmpty() || isMissing(value)) {
                continue;
            }
            List<Object> values = new ArrayList<>();
            if (value instanceof Iterable) {
                for (Object item : (Iterable<?>) value) {
                    values.add(item);
                }
            } else {
                values.add(value);
            }
            for (Object item : values) {
                if (!enumValues.contains(stringifyParam(item))) {
                    throw new IllegalArgumentException("invalid " + location + " parameter " + name
                            + ": expected one of " + String.join(", ", enumValues));
                }
            }
        }
    }

    private static boolean isMissing(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return ((String) value).isEmpty();
        }
        if (value instanceof List) {
            return ((List<?>) value).isEmpty();
        }
        return false;
    }

    private BuiltRequest multipartBody(String url, List<FormParam> formParams, Map<String, Object> params) {
        String boundary = "crawlora-" + UUID.randomUUID().toString().replace("-", "");
        StringBuilder chunks = new StringBuilder();
        for (FormParam parameter : formParams) {
            Object value = params.get(parameter.name);
            if (value == null) {
                continue;
            }
            chunks.append("--").append(boundary).append("\r\n");
            chunks.append("Content-Disposition: form-data; name=\"").append(parameter.name).append("\"\r\n\r\n");
            chunks.append(value).append("\r\n");
        }
        chunks.append("--").append(boundary).append("--\r\n");
        byte[] body = chunks.toString().getBytes(StandardCharsets.UTF_8);
        return new BuiltRequest(url, body, Map.of("content-type", "multipart/form-data; boundary=" + boundary));
    }

    // ---- header / auth helpers --------------------------------------------

    private static Map<String, String> authHeaders(List<String> security, String apiKey, String jwtToken) {
        Map<String, String> result = new LinkedHashMap<>();
        if (security.contains("ApiKeyAuth") && !apiKey.isEmpty()) {
            result.put("x-api-key", apiKey);
        }
        if (security.contains("JWTAuth") && !jwtToken.isEmpty()) {
            String lower = jwtToken.toLowerCase();
            boolean prefixed = lower.startsWith("token ") || lower.startsWith("bearer ");
            result.put("Authorization", prefixed ? jwtToken : "Token " + jwtToken);
        }
        return result;
    }

    @SafeVarargs
    private static Map<String, String> mergeHeaders(Map<String, String>... sources) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Map<String, String> source : sources) {
            for (Map.Entry<String, String> entry : source.entrySet()) {
                String name = entry.getKey();
                String lower = name.toLowerCase();
                String existing = names.get(lower);
                if (existing != null && !existing.equals(name)) {
                    result.remove(existing);
                }
                result.put(name, entry.getValue() == null ? "" : entry.getValue());
                names.put(lower, name);
            }
        }
        return result;
    }

    private static String validateResponseType(String responseType) {
        if (RESPONSE_TYPES.contains(responseType)) {
            return responseType;
        }
        throw new IllegalArgumentException("invalid responseType: expected one of " + String.join(", ", RESPONSE_TYPES));
    }

    private static Object parseResponse(String body, String contentType, String responseType) {
        if (responseType.equals("text")) {
            return body;
        }
        if (responseType.equals("json") || contentType.toLowerCase().contains("application/json")) {
            return body.isEmpty() ? null : Json.parse(body);
        }
        return body;
    }

    private static String stringifyParam(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "true" : "false";
        }
        return String.valueOf(value);
    }

    private static String urlEscape(Object value) {
        return formEncode(String.valueOf(value));
    }

    private static String formEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String ensureRequestId(Map<String, String> headers) {
        String existing = headerValue(headers, "x-request-id");
        if (!existing.isEmpty()) {
            return existing;
        }
        String requestId = UUID.randomUUID().toString().replace("-", "");
        headers.put("X-Request-Id", requestId);
        return requestId;
    }

    private static Double retryAfterDelay(Map<String, String> headers, double cap) {
        String value = headerValue(headers, "retry-after");
        if (value.isEmpty()) {
            return null;
        }
        try {
            double seconds = Double.parseDouble(value.trim());
            if (seconds > 0) {
                return Math.min(seconds, cap);
            }
        } catch (NumberFormatException ignored) {
            // fall through to HTTP-date parsing
        }
        try {
            ZonedDateTime target = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
            double delay = (target.toInstant().toEpochMilli() - System.currentTimeMillis()) / 1000.0;
            if (delay > 0) {
                return Math.min(delay, cap);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String headerValue(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue() == null ? "" : entry.getValue();
            }
        }
        return "";
    }

    private static boolean isTimeoutError(Throwable exc) {
        Throwable e = exc;
        while (e != null) {
            if (e instanceof java.net.http.HttpTimeoutException || e instanceof java.net.SocketTimeoutException) {
                return true;
            }
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("timed out")) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    private static Map<String, Object> stringifyKeys(Map<String, Object> params) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return out;
    }

    // ---- builder -----------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent configuration for {@link CrawloraClient}. */
    public static final class Builder {
        private String apiKey;
        private String jwtToken;
        private String baseUrl;
        private double timeout = 30;
        private int retries = 0;
        private double retryDelay = 0.25;
        private double maxRetryDelay = DEFAULT_MAX_RETRY_DELAY;
        private Set<Integer> retryStatuses;
        private RetryPredicate retryPredicate;
        private RetryHook onRetry;
        private boolean requestId = false;
        private boolean idempotencyKeys = false;
        private Double rateLimit;
        private Integer maxConcurrency;
        private Logger logger;
        private final List<BeforeRequest> beforeRequest = new ArrayList<>();
        private final List<AfterResponse> afterResponse = new ArrayList<>();
        private Map<String, String> headers;
        private String userAgent = DEFAULT_USER_AGENT;
        private Transport transport;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder jwtToken(String jwtToken) {
            this.jwtToken = jwtToken;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(double timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }

        public Builder retryDelay(double retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }

        public Builder maxRetryDelay(double maxRetryDelay) {
            this.maxRetryDelay = maxRetryDelay;
            return this;
        }

        public Builder retryStatuses(Set<Integer> retryStatuses) {
            this.retryStatuses = retryStatuses;
            return this;
        }

        public Builder retryPredicate(RetryPredicate retryPredicate) {
            this.retryPredicate = retryPredicate;
            return this;
        }

        public Builder onRetry(RetryHook onRetry) {
            this.onRetry = onRetry;
            return this;
        }

        public Builder requestId(boolean requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder idempotencyKeys(boolean idempotencyKeys) {
            this.idempotencyKeys = idempotencyKeys;
            return this;
        }

        public Builder rateLimit(double rateLimit) {
            this.rateLimit = rateLimit;
            return this;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder beforeRequest(BeforeRequest hook) {
            this.beforeRequest.add(hook);
            return this;
        }

        public Builder afterResponse(AfterResponse hook) {
            this.afterResponse.add(hook);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        public CrawloraClient build() {
            return new CrawloraClient(this);
        }
    }
}
