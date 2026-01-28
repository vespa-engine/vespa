// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.mcp;

import ai.vespa.mcp.api.McpSpecProvider;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.HttpRequest.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.yahoo.container.jdisc.HttpRequest.createTestRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link McpRequestHandler}.
 *
 * @author Edvard Dingsør
 */
public class McpRequestHandlerTest {

    private McpRequestHandler handler;
    private Executor executor;
    private Metric mockMetric;

    @BeforeEach
    public void setUp() {
        mockMetric = Mockito.mock(Metric.class);
        executor = Executors.newSingleThreadExecutor();
        var registry = new ComponentRegistry<McpSpecProvider>();
        handler = new McpRequestHandler(executor, mockMetric, registry);
    }

    @Test
    public void testInvalidPathReturns404() {
        var request = createTestRequest(
                "http://localhost:8080/invalid/path",
                Method.POST,
                new ByteArrayInputStream(new byte[0])
        );
        var response = handler.handle(request, null);
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testUnsupportedMethodReturns405() {
        var request = createTestRequest("http://localhost:8080/mcp/", Method.PUT);
        var response = handler.handle(request);
        assertEquals(405, response.getStatus());
    }

    @Test
    public void testGetRoutesToTransport() {
        var request = createTestRequest(
                "http://localhost:8080/mcp/",
                Method.GET,
                new ByteArrayInputStream(new byte[0])
        );
        var response = handler.handle(request, null);
        assertEquals(405, response.getStatus());
    }

    @Test
    public void testDeleteRoutesToTransport() {
        var request = createTestRequest(
                "http://localhost:8080/mcp/",
                Method.DELETE,
                new ByteArrayInputStream(new byte[0])
        );
        var response = handler.handle(request, null);
        assertEquals(405, response.getStatus());
    }

    @Test
    public void testPostReturnsValidJsonRpcResponse() throws IOException {
        var requestBody = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"params\":{},\"id\":1}";
        var request = createTestRequest(
                "http://localhost:8080/mcp/",
                Method.POST,
                new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8))
        );
        request.getJDiscRequest().headers().add("Accept", "application/json");

        var response = handler.handle(request, null);
        assertEquals(200, response.getStatus());

        var out = new ByteArrayOutputStream();
        response.render(out);
        var body = out.toString(StandardCharsets.UTF_8);
        assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}", body);
    }

    @Test
    public void testPostWithInvalidJsonReturns400() throws IOException {
        var requestBody = "not valid json";
        var request = createTestRequest(
                "http://localhost:8080/mcp/",
                Method.POST,
                new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8))
        );
        var response = handler.handle(request, null);
        assertEquals(400, response.getStatus());
    }
}
