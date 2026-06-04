package net.crawlora;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Test transport double: records every call and returns canned responses (a
 * queue consumed in order, or a function applied to the recorded call).
 */
final class RecordingTransport implements Transport {
    static final class Call {
        final String method;
        final String url;
        final Map<String, String> headers;
        final byte[] body;
        final double timeout;

        Call(String method, String url, Map<String, String> headers, byte[] body, double timeout) {
            this.method = method;
            this.url = url;
            this.headers = headers;
            this.body = body;
            this.timeout = timeout;
        }
    }

    final List<Call> calls = new ArrayList<>();
    private final List<TransportResponse> queue;
    private final Function<Call, TransportResponse> fn;

    RecordingTransport(List<TransportResponse> responses) {
        this.queue = new ArrayList<>(responses);
        this.fn = null;
    }

    RecordingTransport(Function<Call, TransportResponse> fn) {
        this.queue = null;
        this.fn = fn;
    }

    @Override
    public TransportResponse call(String method, String url, Map<String, String> headers, byte[] body, double timeout) {
        Call c = new Call(method, url, headers, body, timeout);
        calls.add(c);
        if (fn != null) {
            return fn.apply(c);
        }
        return queue.remove(0);
    }

    static TransportResponse json(int status, String body) {
        return new TransportResponse(status, Map.of("content-type", "application/json"),
                body.getBytes(StandardCharsets.UTF_8));
    }

    static TransportResponse ok(String dataJson) {
        return json(200, "{\"code\":200,\"msg\":\"OK\",\"data\":" + dataJson + "}");
    }
}
