# Crawlora Java SDK recipes

Common patterns beyond the README. See [`operations.md`](operations.md) for the
full list of operations.

## Authentication

```java
// API key (most endpoints):
CrawloraClient.builder().apiKey("live_…").build();

// JWT (dashboard/user endpoints). A raw token is sent as "Token <jwt>";
// pass "Bearer <jwt>" yourself to override the scheme.
CrawloraClient.builder().jwtToken("eyJ…").build();
```

Both fall back to environment variables: `CRAWLORA_API_KEY` and
`CRAWLORA_BASE_URL`.

## Reddit and Brand

Newer platforms are grouped like every other endpoint:

```java
Object posts = client.reddit().search(Map.of("q", "java", "subreddit", "programming"));
Object brand = client.brand().retrieve(Map.of("domain", "stripe.com"));
```

## Software, Reviews, And Market Datasets

Build a Chrome extension competitive-intelligence view without downloading the
whole catalog: create a high-adoption shortlist, load chart-ready market
metrics, watch movers, and audit permission changes or one item's history.

```java
Object extensions = client.datasets().chromeExtensionsSearch(Map.of("q", "productivity", "min_users", 10000, "sort", "users_desc", "page_size", 20));
Object metrics = client.datasets().chromeExtensionsMetrics(Map.of("days", 30, "limit", 10));
Object movers = client.datasets().chromeExtensionsTrending(Map.of("item_type", "extension", "page_size", 20));
Object permissionChanges = client.datasets().chromeExtensionsChanges(Map.of("change_type", "permissions", "limit", 25));
Object history = client.datasets().chromeExtensionsHistory(Map.of("id", "fjgncogppolhfdpijihbpfmeohpaadpc", "limit", 90));

Object cities = client.datasets().numbeoCitiesSearch(Map.of("country", "Portugal", "sort", "quality_of_life_desc"));
Object software = client.capterra().search(Map.of("q", "project management"));
Object games = client.metacritic().browse(Map.of("type", "game", "sort", "score"));
```

## Airbnb Host Profiles

Look up a public Airbnb host, then page through their listings and guest reviews.

```java
Object host = client.airbnb().host(Map.of("id", "65056940"));
Object listings = client.airbnb().hostListings(Map.of("id", "65056940", "page", 1));
Object reviews = client.airbnb().hostReviews(Map.of("id", "65056940", "page", 1));
```

## TrustMRR Verified Startup Revenues

Browse verified startup revenues and the acquisition marketplace on TrustMRR: the marketplace snapshot, the revenue leaderboard, startup detail, and categories.

```java
Object deals = client.trustMrr().trustmrrMarketplace(Map.of());
Object board = client.trustMrr().trustmrrLeaderboard(Map.of("metric", "mrr"));
Object startup = client.trustMrr().trustmrrStartup(Map.of("slug", "stan"));
Object cats = client.trustMrr().trustmrrCategories(Map.of());
Object saas = client.trustMrr().trustmrrCategory(Map.of("slug", "saas"));
```

## Retries and Retry-After

```java
CrawloraClient.builder()
        .retries(3)
        .retryDelay(0.5)                       // exponential backoff with jitter
        .maxRetryDelay(10)
        .retryStatuses(java.util.Set.of(429, 503))  // override the default retryable set
        .onRetry((attempt, error, delay) ->
                System.err.println("retry " + attempt + " after " + delay + "s (" + error.getStatus() + ")"))
        .build();
```

A custom predicate wins over the status set:

```java
CrawloraClient.builder()
        .retries(2)
        .retryPredicate((status, error) -> status == 429)
        .build();
```

`Retry-After` (seconds or HTTP-date) is always honored, capped at
`maxRetryDelay`.

## Hooks

```java
import java.util.Map;

CrawloraClient client = CrawloraClient.builder()
        .beforeRequest(ctx -> ctx.headers.put("X-Trace-Id", java.util.UUID.randomUUID().toString()))
        .afterResponse((operationId, status, headers, body) -> {
            if (body instanceof Map) {
                Map<String, Object> copy = new java.util.LinkedHashMap<>((Map<String, Object>) body);
                copy.put("_op", operationId);
                copy.put("_status", status);
                return copy;
            }
            return null; // keep body unchanged
        })
        .build();
```

`beforeRequest` receives a mutable `RequestContext` (`operation`, `method`,
`url`, `headers`); editing `url`/`headers` rewrites the outgoing request.
`afterResponse` may return a replacement body (return `null` to keep it).

## Rate limiting and concurrency

```java
CrawloraClient client = CrawloraClient.builder()
        .rateLimit(10)
        .maxConcurrency(4)   // throttled to 10 rps / 4 in-flight
        .build();

queries.parallelStream().forEach(q -> client.bing().search(Map.of("q", q)));
```

## Response modes

```java
import net.crawlora.RequestOptions;

client.request("youtube-transcript", Map.of("id", "abc"),
        new RequestOptions().responseType("text"));   // String

Object stream = client.request("bing-search", Map.of("q", "x"),
        new RequestOptions().responseType("stream"));  // java.io.InputStream
```

`auto` (default) parses JSON when the response is JSON and returns text
otherwise.

## Custom transport (testing)

Implement the `Transport` functional interface and return a `TransportResponse`:

```java
import net.crawlora.*;

Transport fake = (method, url, headers, body, timeout) ->
        new TransportResponse(200, Map.of("content-type", "application/json"),
                "{\"data\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

CrawloraClient client = CrawloraClient.builder().transport(fake).build();
```
