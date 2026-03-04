package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.HealthMetric;
import ai.vespa.metricsproxy.metric.model.StatusCode;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemoteHealthStatusFetcherTest {

  private static final String HEALTH_PATH = "/state/v1/health";

  private final RemoteHealthStatusFetcher fetcher = new RemoteHealthStatusFetcher(null, 0);

  @BeforeClass
  public static void setup() {
    HttpMetricFetcher.CONNECTION_TIMEOUT = 60000;
  }

  @Test
  public void testNon200ResponseReturnsUnknownWithoutParsingBody() throws IOException {
    MockHttpServer httpServer = new MockHttpServer("{\"status\": {\"code\": \"UP\"}}", HEALTH_PATH);
    try {
      httpServer.setResponse("<html><body>Service not available</body></html>");
      httpServer.setStatusCode(503);

      VespaService service = VespaService.create("service1", "id", httpServer.port());
      RemoteHealthStatusFetcher healthFetcher = new RemoteHealthStatusFetcher(service, httpServer.port());
      HealthMetric result = healthFetcher.getHealth(1);

      assertEquals(StatusCode.UNKNOWN, result.getStatus());
      assertTrue(result.getMessage().contains("503"));
    } finally {
      httpServer.close();
    }
  }

  @Test
  public void testParseValidJson() {
    String json = "{\"status\": {\"code\": \"UP\", \"message\": \"Service is healthy\"}}";
    InputStream data = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    HealthMetric result = fetcher.parse(data);

    assertEquals(StatusCode.UP, result.getStatus());
    assertEquals("Service is healthy", result.getMessage());
  }

  @Test
  public void testParseValidJsonWithoutMessage() {
    String json = "{\"status\": {\"code\": \"UP\"}}";
    InputStream data = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    HealthMetric result = fetcher.parse(data);

    assertEquals(StatusCode.UP, result.getStatus());
    assertEquals("", result.getMessage());
  }

  @Test
  public void testParseEmptyJson() {
    String json = "{}";
    InputStream data = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    HealthMetric result = fetcher.parse(data);

    assertEquals(StatusCode.UNKNOWN, result.getStatus());
    assertTrue(result.getMessage().contains("Empty metrics response"));
  }

  @Test
  public void testParseMissingStatus() {
    String json = "{\"other\": \"field\"}";
    InputStream data = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    HealthMetric result = fetcher.parse(data);

    assertEquals(StatusCode.UNKNOWN, result.getStatus());
    assertTrue(result.getMessage().contains("Missing status or code"));
  }

  @Test
  public void testParseMissingCode() {
    String json = "{\"status\": {\"message\": \"No code here\"}}";
    InputStream data = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    HealthMetric result = fetcher.parse(data);

    assertEquals(StatusCode.UNKNOWN, result.getStatus());
    assertTrue(result.getMessage().contains("Missing status or code"));
  }

  @Test
  public void testParseInvalidJson() {
    String invalidJson = "{invalid json}";
    InputStream data = new ByteArrayInputStream(invalidJson.getBytes(StandardCharsets.UTF_8));

    HealthMetric result = fetcher.parse(data);

    assertEquals(StatusCode.UNKNOWN, result.getStatus());
    assertTrue(result.getMessage().contains("Not able to parse json"));
  }

  @Test
  public void testParseUnreadableInput() {
    InputStream unreadableStream = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("Stream error");
      }
    };

    HealthMetric result = fetcher.parse(unreadableStream);

    assertEquals(StatusCode.UNKNOWN, result.getStatus());
    assertTrue(result.getMessage().contains("Not able to parse json"));
  }
}
