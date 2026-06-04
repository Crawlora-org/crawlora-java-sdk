package net.crawlora;

import java.util.concurrent.Semaphore;

/**
 * Optional client-side throttle: caps concurrency and spaces requests to a
 * maximum rate (requests per second).
 */
final class RateLimiter {
    private final double interval;
    private final Semaphore semaphore;
    private final Object lock = new Object();
    private long nextNanos = 0L;

    RateLimiter(Double rps, Integer concurrency) {
        this.interval = (rps != null && rps > 0) ? 1.0 / rps : 0.0;
        this.semaphore = (concurrency != null && concurrency > 0) ? new Semaphore(concurrency) : null;
    }

    void acquire() throws InterruptedException {
        if (semaphore != null) {
            semaphore.acquire();
        }
        if (interval > 0) {
            long waitNanos;
            synchronized (lock) {
                long now = System.nanoTime();
                long intervalNanos = (long) (interval * 1_000_000_000L);
                waitNanos = Math.max(0L, nextNanos - now);
                nextNanos = Math.max(now, nextNanos) + intervalNanos;
            }
            if (waitNanos > 0) {
                Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
            }
        }
    }

    void release() {
        if (semaphore != null) {
            semaphore.release();
        }
    }
}
