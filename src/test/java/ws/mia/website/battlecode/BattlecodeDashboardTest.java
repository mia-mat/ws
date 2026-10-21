package ws.mia.website.battlecode;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "battlecode.feed=https://example.invalid/status.json")
class BattlecodeDashboardTest {

    private static final String FEED_ATTRIBUTE = "data-feed=\"https://example.invalid/status.json\"";

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String forwardedHost) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (forwardedHost != null) request.header("X-Forwarded-Host", forwardedHost);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void dashboardHostGetsTheDashboardAtRoot() throws Exception {
        HttpResponse<String> response = get("/", "battlecode.mia.ws");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(FEED_ATTRIBUTE));
    }

    @Test
    void hostMatchIgnoresCaseAndPort() throws Exception {
        assertTrue(get("/", "BattleCode.mia.ws:443").body().contains(FEED_ATTRIBUTE));
    }

    @Test
    void mainSiteIsUnchanged() throws Exception {
        for (String host : new String[]{"mia.ws", null}) {
            HttpResponse<String> response = get("/", host);
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("hi, i'm mia"));
            assertFalse(response.body().contains("data-feed"));
        }
    }

    @Test
    void dashboardPathWorksOnAnyHost() throws Exception {
        assertTrue(get("/battlecode/", "ws-staging.mimi.pet").body().contains(FEED_ATTRIBUTE));
        assertTrue(get("/battlecode", null).body().contains(FEED_ATTRIBUTE));
    }

    @Test
    void assetsAreServed() throws Exception {
        assertEquals(200, get("/battlecode/dashboard.js", "battlecode.mia.ws").statusCode());
        assertEquals(200, get("/battlecode/dashboard.css", "battlecode.mia.ws").statusCode());
    }

}
