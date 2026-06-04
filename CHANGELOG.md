# Changelog

## 1.5.0-sdk.1

- Initial release of the Crawlora Java SDK.
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
