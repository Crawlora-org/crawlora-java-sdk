package net.crawlora;

import java.util.Map;

/** A raw HTTP response returned by a {@link Transport}. */
public final class TransportResponse {
    public final int status;
    public final Map<String, String> headers;
    public final byte[] body;

    public TransportResponse(int status, Map<String, String> headers, byte[] body) {
        this.status = status;
        this.headers = headers == null ? Map.of() : headers;
        this.body = body == null ? new byte[0] : body;
    }

    public int getStatus() {
        return status;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }
}
