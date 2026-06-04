# Changelog

## 1.5.0-sdk.2

- Packaging: expand the Maven `<description>` for a richer package listing, set
  the project URL to https://crawlora.net/, and fix the `<scm>` URLs to point at
  the `Crawlora-org` GitHub organization. No client or API changes.

## 1.5.0-sdk.1

- Initial release of the Crawlora Java SDK, published to Maven Central under the
  `net.crawlora` namespace (GPG-signed artifacts).
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
