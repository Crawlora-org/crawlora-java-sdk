package net.crawlora;

import java.util.Map;

/** Receives a structured log event for each request/retry. */
@FunctionalInterface
public interface Logger {
    void log(Map<String, Object> event);
}
