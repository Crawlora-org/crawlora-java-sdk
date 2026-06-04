package net.crawlora;

/** Invoked before each retry with the attempt number, error, and delay (seconds). */
@FunctionalInterface
public interface RetryHook {
    void onRetry(int attempt, CrawloraError error, double delay);
}
