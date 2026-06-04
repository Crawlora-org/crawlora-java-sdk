import net.crawlora.CrawloraClient;

import java.util.List;
import java.util.Map;

/**
 * Minimal Bing search example.
 *
 * <p>Run with the SDK on the classpath, e.g.:
 * {@code java -cp target/crawlora-sdk-1.5.0-sdk.1.jar examples/BingSearch.java}
 * (JDK 17+ single-file source mode).
 */
public final class BingSearch {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        CrawloraClient client = CrawloraClient.builder()
                .apiKey(System.getenv("CRAWLORA_API_KEY"))
                .build();

        Map<String, Object> result =
                (Map<String, Object>) client.bing().search(Map.of("q", "web scraping"));

        for (Object item : (List<Object>) result.get("data")) {
            System.out.println(((Map<String, Object>) item).get("title"));
        }
    }
}
