package net.crawlora;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.crawlora.groups.BingGroup;
import org.junit.jupiter.api.Test;

class CrawloraClientTest {

    private CrawloraClient client(List<TransportResponse> responses) {
        return CrawloraClient.builder().apiKey("secret")
                .transport(new RecordingTransport(responses)).build();
    }

    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    @Test
    void groupedCallSendsApiKeyAndParsesJson() {
        RecordingTransport transport = new RecordingTransport(
                List.of(RecordingTransport.ok("[{\"title\":\"hit\"}]")));
        CrawloraClient c = CrawloraClient.builder().apiKey("secret").transport(transport).build();
        Map<String, Object> result = (Map<String, Object>) c.bing().search(params("q", "web scraping"));
        List<Object> data = (List<Object>) result.get("data");
        assertEquals("hit", ((Map<String, Object>) data.get(0)).get("title"));
        RecordingTransport.Call call = transport.calls.get(0);
        assertEquals("GET", call.method);
        assertTrue(call.url.contains("/bing/search"));
        assertTrue(call.url.contains("q=web%20scraping"));
        assertEquals("secret", call.headers.get("x-api-key"));
        assertTrue(call.headers.get("User-Agent").startsWith("crawlora-java-sdk/"));
    }

    @Test
    void dynamicRequestUnknownOperationThrows() {
        CrawloraClient c = client(List.of());
        assertThrows(IllegalArgumentException.class, () -> c.request("does-not-exist", params(), null));
    }

    @Test
    void missingRequiredQueryParamThrows() {
        CrawloraClient c = client(List.of(RecordingTransport.ok("[]")));
        assertThrows(IllegalArgumentException.class, () -> c.bing().search(params()));
    }

    @Test
    void missingRequiredPathParamThrows() {
        CrawloraClient c = client(List.of(RecordingTransport.ok("[]")));
        assertThrows(IllegalArgumentException.class, () -> c.request("amazon-product", params(), null));
    }

    @Test
    void enumValidationRejectsBadValue() {
        CrawloraClient c = client(List.of(RecordingTransport.ok("[]")));
        IllegalArgumentException err = assertThrows(IllegalArgumentException.class,
                () -> c.groupOf("amazon").call("product", params("asin", "B000", "language", "fr_FR")));
        assertTrue(err.getMessage().contains("language"));
    }

    @Test
    void enumValidationAcceptsGoodValue() {
        RecordingTransport transport = new RecordingTransport(List.of(RecordingTransport.ok("{}")));
        CrawloraClient c = CrawloraClient.builder().apiKey("k").transport(transport).build();
        c.groupOf("amazon").call("product", params("asin", "B000", "language", "en_US"));
        assertTrue(transport.calls.get(0).url.contains("language=en_US"));
    }

    @Test
    void unexpectedParamForGroupCallThrows() {
        CrawloraClient c = client(List.of(RecordingTransport.ok("[]")));
        IllegalArgumentException err = assertThrows(IllegalArgumentException.class,
                () -> c.bing().search(params("q", "x", "nope", 1)));
        assertTrue(err.getMessage().contains("unexpected parameter"));
    }

    @Test
    void jwtAuthHeader() {
        // referrals-me requires JWTAuth in this contract.
        RecordingTransport transport = new RecordingTransport(List.of(RecordingTransport.ok("{}")));
        CrawloraClient c = CrawloraClient.builder().jwtToken("abc").transport(transport).build();
        Map.Entry<String, Operation> op = findOperation(o -> o.security.contains("JWTAuth"));
        assertNotNull(op, "no JWTAuth operation in contract");
        c.request(op.getKey(), requiredStub(op.getValue()), null);
        assertEquals("Token abc", transport.calls.get(0).headers.get("Authorization"));
    }

    @Test
    void clientErrorOn4xx() {
        CrawloraClient c = client(List.of(
                RecordingTransport.json(400, "{\"code\":400,\"msg\":\"bad request\"}")));
        CrawloraClientError err = assertThrows(CrawloraClientError.class,
                () -> c.bing().search(params("q", "x")));
        assertEquals(400, err.getStatus());
        assertEquals(Integer.valueOf(400), err.getCode());
        assertEquals("bad request", err.getMessage());
    }

    @Test
    void serverErrorOn5xx() {
        CrawloraClient c = client(List.of(RecordingTransport.json(500, "{\"msg\":\"boom\"}")));
        CrawloraServerError err = assertThrows(CrawloraServerError.class,
                () -> c.bing().search(params("q", "x")));
        assertEquals(500, err.getStatus());
    }

    @SuppressWarnings("unchecked")
    @Test
    void retryOn500ThenSuccess() {
        RecordingTransport transport = new RecordingTransport(new ArrayList<>(List.of(
                RecordingTransport.json(500, "{\"msg\":\"boom\"}"),
                RecordingTransport.ok("[{\"ok\":true}]"))));
        CrawloraClient c = CrawloraClient.builder().apiKey("k").transport(transport)
                .retries(1).retryDelay(0).build();
        Map<String, Object> result = (Map<String, Object>) c.bing().search(params("q", "x"));
        assertEquals(1, ((List<Object>) result.get("data")).size());
        assertEquals(2, transport.calls.size());
    }

    @Test
    void noRetryWhenNotRetryable() {
        RecordingTransport transport = new RecordingTransport(new ArrayList<>(List.of(
                RecordingTransport.json(400, "{\"msg\":\"nope\"}"))));
        CrawloraClient c = CrawloraClient.builder().apiKey("k").transport(transport)
                .retries(3).retryDelay(0).build();
        assertThrows(CrawloraClientError.class, () -> c.bing().search(params("q", "x")));
        assertEquals(1, transport.calls.size());
    }

    @Test
    void retryAfterHeaderRespected() {
        List<Double> delays = new ArrayList<>();
        RecordingTransport transport = new RecordingTransport(new ArrayList<>(List.of(
                new TransportResponse(429,
                        Map.of("content-type", "application/json", "retry-after", "1"),
                        "{\"msg\":\"slow\"}".getBytes(StandardCharsets.UTF_8)),
                RecordingTransport.ok("[]"))));
        CrawloraClient c = CrawloraClient.builder().apiKey("k").transport(transport)
                .retries(1).retryDelay(0.01)
                .onRetry((attempt, error, delay) -> delays.add(delay)).build();
        c.bing().search(params("q", "x"));
        // Retry-After: 1 overrides the tiny exponential backoff.
        assertEquals(List.of(1.0), delays);
    }

    @Test
    void textResponseMode() {
        CrawloraClient c = client(List.of(
                new TransportResponse(200, Map.of("content-type", "text/plain"),
                        "plain transcript".getBytes(StandardCharsets.UTF_8))));
        Object result = c.request("youtube-transcript", params("id", "abc"),
                new RequestOptions().responseType("text"));
        assertEquals("plain transcript", result);
    }

    @Test
    void streamResponseReturnsInputStream() throws Exception {
        CrawloraClient c = client(List.of(RecordingTransport.json(200, "raw-bytes")));
        Object result = c.request("bing-search", params("q", "x"),
                new RequestOptions().responseType("stream"));
        assertInstanceOf(InputStream.class, result);
        byte[] bytes = ((InputStream) result).readAllBytes();
        assertEquals("raw-bytes", new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void beforeRequestHookMutatesHeaders() {
        RecordingTransport transport = new RecordingTransport(List.of(RecordingTransport.ok("{}")));
        CrawloraClient c = CrawloraClient.builder().apiKey("k").transport(transport)
                .beforeRequest(ctx -> ctx.headers.put("X-Custom", "yes")).build();
        c.bing().search(params("q", "x"));
        assertEquals("yes", transport.calls.get(0).headers.get("X-Custom"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void afterResponseHookReplacesBody() {
        CrawloraClient c = CrawloraClient.builder().apiKey("k")
                .transport(new RecordingTransport(List.of(RecordingTransport.ok("{\"n\":1}"))))
                .afterResponse((op, status, headers, body) -> Map.of("replaced", true)).build();
        Map<String, Object> result = (Map<String, Object>) c.bing().search(params("q", "x"));
        assertEquals(Boolean.TRUE, result.get("replaced"));
    }

    @Test
    void requestIdAddedWhenEnabled() {
        RecordingTransport transport = new RecordingTransport(List.of(RecordingTransport.ok("{}")));
        CrawloraClient c = CrawloraClient.builder().apiKey("k").transport(transport)
                .requestId(true).build();
        c.bing().search(params("q", "x"));
        String id = transport.calls.get(0).headers.get("x-request-id");
        assertNotNull(id);
        assertFalse(id.isEmpty());
    }

    @Test
    void idempotencyKeyAddedForPost() {
        Map.Entry<String, Operation> post = findOperation(o -> o.method.equals("POST"));
        assertNotNull(post, "no POST operation in contract");
        RecordingTransport transport = new RecordingTransport(List.of(RecordingTransport.ok("{}")));
        CrawloraClient c = CrawloraClient.builder().apiKey("k").jwtToken("j").transport(transport)
                .idempotencyKeys(true).build();
        c.request(post.getKey(), requiredStub(post.getValue()), null);
        assertNotNull(transport.calls.get(0).headers.get("Idempotency-Key"));
    }

    @Test
    void networkErrorOnTransportThrow() {
        Transport raising = (method, url, headers, body, timeout) -> {
            throw new java.io.IOException("boom");
        };
        CrawloraClient c = CrawloraClient.builder().apiKey("k").transport(raising).build();
        assertThrows(CrawloraNetworkError.class, () -> c.bing().search(params("q", "x")));
    }

    @Test
    void paginateNumericStopsOnEmpty() {
        RecordingTransport transport = new RecordingTransport(new ArrayList<>(List.of(
                RecordingTransport.ok("[{\"i\":1}]"),
                RecordingTransport.ok("[{\"i\":2}]"),
                RecordingTransport.ok("[]"))));
        CrawloraClient c = CrawloraClient.builder().apiKey("k").transport(transport).build();
        List<Object> pages = c.paginate("airbnb-room-reviews", params("id", "r1"), null);
        assertEquals(3, pages.size());
        assertEquals(3, transport.calls.size());
        assertTrue(transport.calls.get(0).url.contains("page=1"));
        assertTrue(transport.calls.get(1).url.contains("page=2"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void paginateItemsExtractsData() {
        RecordingTransport transport = new RecordingTransport(new ArrayList<>(List.of(
                RecordingTransport.ok("[{\"i\":1},{\"i\":2}]"),
                RecordingTransport.ok("[]"))));
        CrawloraClient c = CrawloraClient.builder().apiKey("k").transport(transport).build();
        List<Object> items = c.paginateItems("airbnb-room-reviews", params("id", "r1"), null);
        assertEquals(2, items.size());
        assertEquals(1L, ((Map<String, Object>) items.get(0)).get("i"));
    }

    @Test
    void paginateCursorMode() {
        Map.Entry<String, Operation> cur = findOperation(o -> !o.cursorParams.isEmpty());
        assertNotNull(cur, "no cursor operation in contract");
        String cursorParam = cur.getValue().cursorParams.get(0);
        RecordingTransport transport = new RecordingTransport(new ArrayList<>(List.of(
                RecordingTransport.json(200, "{\"data\":[1],\"next\":\"c2\"}"),
                RecordingTransport.json(200, "{\"data\":[2],\"next\":null}"))));
        CrawloraClient c = CrawloraClient.builder().apiKey("k").transport(transport).build();
        PaginateOptions opts = new PaginateOptions()
                .cursorParam(cursorParam)
                .nextCursor(resp -> ((Map<String, Object>) resp).get("next"));
        List<Object> pages = c.paginate(cur.getKey(), requiredStub(cur.getValue()), opts);
        assertEquals(2, pages.size());
        assertTrue(transport.calls.get(1).url.contains(cursorParam + "=c2"));
    }

    @Test
    void invalidResponseTypeThrows() {
        CrawloraClient c = client(List.of(RecordingTransport.ok("{}")));
        assertThrows(IllegalArgumentException.class,
                () -> c.request("bing-search", params("q", "x"), new RequestOptions().responseType("xml")));
    }

    @Test
    void typedGroupNoArgOverloadWorks() {
        // bing-suggest has only optional params, so the no-arg overload exists.
        RecordingTransport transport = new RecordingTransport(List.of(RecordingTransport.ok("[]")));
        CrawloraClient c = CrawloraClient.builder().apiKey("k").transport(transport).build();
        BingGroup bing = c.bing();
        bing.suggest(params("q", "x"));
        assertTrue(transport.calls.get(0).url.contains("/bing/suggest"));
    }

    // Exercises the real DefaultTransport against an in-process HttpServer.
    @SuppressWarnings("unchecked")
    @Test
    void defaultTransportAgainstRealServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String payload = "{\"code\":200,\"msg\":\"OK\",\"data\":{\"echo\":\""
                    + exchange.getRequestURI().getPath() + "\"}}";
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            CrawloraClient c = CrawloraClient.builder().apiKey("k")
                    .baseUrl("http://127.0.0.1:" + port + "/api/v1").build();
            Map<String, Object> result = (Map<String, Object>) c.bing().search(params("q", "real"));
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertTrue(((String) data.get("echo")).contains("/api/v1/bing/search"));
        } finally {
            server.stop(0);
        }
    }

    // ---- helpers ----------------------------------------------------------

    private interface OpFilter {
        boolean test(Operation operation);
    }

    private Map.Entry<String, Operation> findOperation(OpFilter filter) {
        for (Map.Entry<String, Operation> entry : Operations.OPERATIONS.entrySet()) {
            if (filter.test(entry.getValue())) {
                return entry;
            }
        }
        return null;
    }

    private Map<String, Object> requiredStub(Operation operation) {
        Map<String, Object> p = new HashMap<>();
        for (String name : operation.pathParams) {
            p.put(name, "x");
        }
        for (QueryParam q : operation.queryParams) {
            if (q.required) {
                p.put(q.name, q.enumValues.isEmpty() ? "x" : q.enumValues.get(0));
            }
        }
        for (FormParam f : operation.formParams) {
            if (f.required) {
                p.put(f.name, "x");
            }
        }
        if (operation.bodyRequired) {
            p.put(operation.bodyParam, Map.of("stub", true));
        }
        return p;
    }
}
