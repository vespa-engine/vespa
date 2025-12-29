package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.HealthMetric;
import ai.vespa.metricsproxy.metric.model.StatusCode;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemoteHealthStatusFetcherTest {

  private final RemoteHealthStatusFetcher fetcher = new RemoteHealthStatusFetcher(null, 0);

  @Test
  public void testParseValidJson() {
    String json = "{\"status\": {\"code\": \"UP\", \"message\": \"Service is healthy\"}}";
    InputStream data = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    HealthMetric result = fetcher.parse(data);

    assertEquals(StatusCode.UP, result.getStatus());
    assertEquals("Service is healthy", result.getMessage());
  }

  @Test
  public void testParseMissingStatusField() {
    String json = "{}";
    InputStream data = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    HealthMetric result = fetcher.parse(data);

    assertEquals(StatusCode.UNKNOWN, result.getStatus());
    assertTrue(result.getMessage().contains("Empty metrics response"));
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
      public int read() {
        throw new RuntimeException("Stream error");
      }
    };

    HealthMetric result = fetcher.parse(unreadableStream);

    assertEquals(StatusCode.UNKNOWN, result.getStatus());
    assertTrue(result.getMessage().contains("Not able to parse json"));
  }
}