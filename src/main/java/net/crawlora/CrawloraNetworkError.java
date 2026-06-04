package net.crawlora;

import java.util.Map;

/** Raised for transport failures and timeouts before a response arrived. */
public class CrawloraNetworkError extends CrawloraError {
    public CrawloraNetworkError(String message, String requestId, Throwable cause) {
        super(message, 0, null, null, "", Map.of(), requestId, cause);
    }
}
