# Crawlora Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/net.crawlora/crawlora-sdk?label=Maven%20Central)](https://central.sonatype.com/artifact/net.crawlora/crawlora-sdk)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Java client for the public [Crawlora](https://crawlora.net) web-scraping API. It
wraps every public endpoint with generated grouped helpers and a dynamic call
interface, plus retries, pagination, middleware hooks, and client-side rate
limiting. The published artifact has **no runtime dependencies** (built on the
JDK's `java.net.http.HttpClient` with a hand-written JSON parser).

- **Base URL:** `https://api.crawlora.net/api/v1`
- **Auth:** API key (`x-api-key`) or JWT (`Authorization`)
- **JDK:** 17+
- Operation reference: [`docs/operations.md`](docs/operations.md) · recipes: [`docs/recipes.md`](docs/recipes.md)

## Install

Published to [Maven Central](https://central.sonatype.com/artifact/net.crawlora/crawlora-sdk) under the `net.crawlora` namespace — no extra repository configuration needed.

Maven:

```xml
<dependency>
  <groupId>net.crawlora</groupId>
  <artifactId>crawlora-sdk</artifactId>
  <version>1.5.0-sdk.2</version>
</dependency>
```

Gradle:

```groovy
implementation "net.crawlora:crawlora-sdk:1.5.0-sdk.2"
```

## Quick start

```java
import net.crawlora.CrawloraClient;
import java.util.List;
import java.util.Map;

// Reads CRAWLORA_API_KEY from the environment if apiKey(...) is omitted.
CrawloraClient client = CrawloraClient.builder()
        .apiKey(System.getenv("CRAWLORA_API_KEY"))
        .build();

@SuppressWarnings("unchecked")
Map<String, Object> result = (Map<String, Object>) client.bing().search(Map.of("q", "web scraping"));
for (Object item : (List<Object>) result.get("data")) {
    System.out.println(((Map<String, Object>) item).get("title"));
}
```

Responses are returned as plain Java values: `Map<String,Object>`,
`List<Object>`, `String`, `Long`/`Double`, `Boolean`, or `null`.

## Calling operations

Grouped helpers map directly to the API (`client.<group>().<method>(params)`).
The `bing()` accessor is built in; reach any other group with a typed group
class or the dynamic accessor:

```java
import net.crawlora.groups.YoutubeGroup;

new YoutubeGroup(client).video(Map.of("id", "dQw4w9WgXcQ"));

// Dynamic, by group name + method name:
client.groupOf("google").call("search", Map.of("q", "crawlora", "country", "US"));
```

Or call any operation dynamically by its id:

```java
client.request("bing-search", Map.of("q", "web scraping", "page", 2), null);

// Discover operations:
net.crawlora.Operations.OPERATION_COUNT;           // total operations
net.crawlora.Operations.GROUPS.get("bing");        // {search=bing-search, ...}
net.crawlora.OperationId.BING_SEARCH;              // "bing-search"
```

## Configuration

```java
CrawloraClient client = CrawloraClient.builder()
        .apiKey("…")
        .timeout(30)             // seconds per request
        .retries(2)              // retry attempts on retryable failures
        .retryDelay(0.25)        // base backoff (exponential + jitter, honors Retry-After)
        .requestId(true)         // attach an x-request-id to every call
        .idempotencyKeys(true)   // stable Idempotency-Key on POST/PATCH
        .rateLimit(5)            // max requests/second (client-side)
        .maxConcurrency(4)       // max in-flight requests across threads
        .headers(Map.of("X-Tenant", "acme"))
        .build();
```

Per-request overrides go through `RequestOptions`:

```java
import net.crawlora.RequestOptions;

client.request("bing-search", Map.of("q", "x"),
        new RequestOptions().responseType("text").timeout(5).retries(0));
```

## Pagination

```java
import net.crawlora.PaginateOptions;

// Numeric (page/offset) — stops on the first empty page:
for (Object review : client.paginateItems("airbnb-room-reviews",
        Map.of("id", "123"), new PaginateOptions().maxPages(5))) {
    System.out.println(((Map<String, Object>) review).get("text"));
}

// Cursor mode — supply the cursor param and a next-cursor extractor:
PaginateOptions opts = new PaginateOptions()
        .cursorParam("cursor")
        .nextCursor(page -> ((Map<String, Object>) page).get("next_cursor"));
for (Object page : client.paginate("producthunt-leaderboard", Map.of(), opts)) {
    System.out.println(((Map<String, Object>) page).get("data"));
}
```

`paginate`/`paginateItems` return a `List`; `paginateStream`/`pageIterator`/
`itemIterator` return a lazy `Stream`/`Iterator`.

## Error handling

```java
import net.crawlora.*;

try {
    client.bing().search(Map.of("q", "x"));
} catch (CrawloraClientError e) {     // 4xx
    System.err.println("rejected (" + e.getStatus() + "): " + e.getMessage() + " " + e.getCode());
} catch (CrawloraServerError e) {     // 5xx
    System.err.println("server error: " + e.getStatus());
} catch (CrawloraNetworkError e) {    // timeout / transport failure
    System.err.println("network: " + e.getMessage());
}
```

All inherit from `CrawloraError`, which exposes `getStatus`, `getCode`,
`getBody`, `getRawBody`, `getHeaders`, and `getRequestId`.

## License

MIT. See [LICENSE](LICENSE).
