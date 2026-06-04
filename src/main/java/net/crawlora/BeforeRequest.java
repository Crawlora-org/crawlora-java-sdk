package net.crawlora;

/** Runs before the request is sent; may mutate the {@link RequestContext}. */
@FunctionalInterface
public interface BeforeRequest {
    void apply(RequestContext context);
}
