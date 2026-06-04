import net.crawlora.CrawloraClient;
import net.crawlora.RequestOptions;

import java.util.Map;

/** Fetch a YouTube transcript as plain text. */
public final class YoutubeTranscript {
    public static void main(String[] args) {
        CrawloraClient client = CrawloraClient.builder()
                .apiKey(System.getenv("CRAWLORA_API_KEY"))
                .build();

        Object transcript = client.request(
                "youtube-transcript",
                Map.of("id", "dQw4w9WgXcQ"),
                new RequestOptions().responseType("text"));

        System.out.println(transcript);
    }
}
