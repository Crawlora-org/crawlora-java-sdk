package net.crawlora;

/**
 * Decides whether a failed request should be retried. Overrides both the default
 * policy and any configured {@code retryStatuses}.
 */
@FunctionalInterface
public interface RetryPredicate {
    boolean shouldRetry(int status, CrawloraError error);
}
