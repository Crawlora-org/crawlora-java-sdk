import net.crawlora.CrawloraClient;
import net.crawlora.PaginateOptions;

import java.util.Map;

/** Paginate Airbnb room reviews, item by item, capped at 5 pages. */
public final class Paginate {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        CrawloraClient client = CrawloraClient.builder()
                .apiKey(System.getenv("CRAWLORA_API_KEY"))
                .build();

        for (Object review : client.paginateItems(
                "airbnb-room-reviews",
                Map.of("id", "12345678"),
                new PaginateOptions().maxPages(5))) {
            System.out.println(((Map<String, Object>) review).get("text"));
        }
    }
}
