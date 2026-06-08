# Changelog

## v1.7.0-sdk.1

- Added six new platforms, regenerated from the public API contract (now 491
  operations): **Polymarket**, **Kalshi**, and **Metaculus** (prediction
  markets); **IMDb**, **Rotten Tomatoes**, and **Box Office Mojo** (film/TV).
- Expanded **Reddit**: subreddit about/comments, multi-subreddit posts,
  domain posts, user posts/comments, and trends.

## 1.6.0-sdk.1

- Added the **Reddit** platform (`client.reddit()`: `search`, `post`,
  `comments`, `subredditPosts`) and the **Brand** platform (`client.brand()`:
  `retrieve`), plus Yahoo Finance `client.yahooFinance().lookup`. Regenerated
  from the public API contract.

## 1.5.0-sdk.3

- Uniform group access: every group is now a first-class typed accessor on the
  client (`client.google()`, `client.youtube()`, …), not just `client.bing()`.
- `CrawloraClient` implements `AutoCloseable` (try-with-resources); the default
  transport's `HttpClient` is released on JDK 21+.
- Cleaner artifact: generated group methods carry full Javadoc (the release
  javadoc build is now warning-free); `x-request-id` is written as
  `X-Request-Id`. No breaking API changes.

## 1.5.0-sdk.2

- First publication to **Maven Central** (`net.crawlora:crawlora-sdk`),
  GPG-signed with sources and javadoc jars; also on GitHub Packages.
- Packaging: expand the Maven `<description>` for a richer package listing, set
  the project URL to https://crawlora.net/, and fix the `<scm>` URLs to point at
  the `Crawlora-org` GitHub organization. No client or API changes.

## 1.5.0-sdk.1

- Initial release of the Crawlora Java SDK in the `net.crawlora` namespace
  (published to GitHub Packages; Maven Central publication followed in sdk.2).
- Generated grouped helpers (`client.bing().search(params)`, plus a typed
  `<Group>Group` class per group and a dynamic `client.groupOf(name).call(...)`)
  and dynamic `request`/`operation` calls for every public operation, generated
  from the shared OpenAPI contract.
- Configurable retries with exponential backoff, jitter, and `Retry-After`
  support; `onRetry` hook.
- Numeric and cursor pagination (`paginate` / `paginateItems` /
  `paginateStream` / `pageIterator` / `itemIterator`).
- `beforeRequest` / `afterResponse` middleware, opt-in `requestId` and
  `idempotencyKeys`, client-side `rateLimit` / `maxConcurrency`.
- `auto` / `json` / `text` / `stream` response modes.
- Typed error hierarchy: `CrawloraError`, `CrawloraClientError`,
  `CrawloraServerError`, `CrawloraNetworkError`.
- Zero runtime dependencies: built on `java.net.http.HttpClient` with a
  hand-written JSON parser/writer.
