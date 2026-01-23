package ai.vespa.mcp;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.HttpRequest.Method;

import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.searchchain.ExecutionFactory;
import com.yahoo.search.searchchain.SearchChainRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.yahoo.container.jdisc.HttpRequest.createTestRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the McpJdiscHandler class.
 *
 * @author Edvard Dingsør
 */
public class McpJdiscHandlerTest {

    private McpJdiscHandler handler;
    private McpHttpTransport mockTransport;
    private JdiscMcpServer mockServerComponent;
    private Executor executor;
    private Metric mockMetric;
    private ExecutionFactory mockExecutionFactory;
    private CompiledQueryProfileRegistry mockCompiledQueryProfileRegistry;

    @BeforeEach
    public void setUp() {
        // Create mocks for dependencies
        mockTransport = Mockito.mock(McpHttpTransport.class);
        mockServerComponent = Mockito.mock(JdiscMcpServer.class);
        mockMetric = Mockito.mock(Metric.class);
        mockExecutionFactory = Mockito.mock(ExecutionFactory.class);
        mockCompiledQueryProfileRegistry = Mockito.mock(CompiledQueryProfileRegistry.class);

        // Mock the SearchChainRegistry to prevent NullPointerException
        when(mockExecutionFactory.searchChainRegistry()).thenReturn(Mockito.mock(SearchChainRegistry.class));

        // Use a real executor for simplicity
        executor = Executors.newSingleThreadExecutor();
        
//        // Set up the mock server component to return the mock transport
//        when(mockServerComponent.getTransport()).thenReturn(mockTransport);
        
        // Create the handler with the mocked dependencies
        handler = new McpJdiscHandler(executor, mockMetric, mockExecutionFactory, mockCompiledQueryProfileRegistry);
    }

    @Test
    public void testInvalidPathReturns404() {
        HttpResponse mockResponse = createMockResponse(404, "{\"error\":\"Not Found\"}");
        when(mockTransport.createErrorResponse(eq(404), any(String.class), any())).thenReturn(mockResponse);

        HttpRequest request = createTestRequest(
                "http://localhost:8080/invalid/path",
                Method.POST,
                new ByteArrayInputStream(new byte[0])
        );
        HttpResponse response = handler.handle(request, null);
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testUnsupportedMethodReturns405() {
        HttpResponse mockResponse = createMockResponse(405, "{\"error\":\"Method not allowed\"}");
        when(mockTransport.createErrorResponse(eq(405), any(String.class), any())).thenReturn(mockResponse);

        HttpRequest request = createTestRequest("http://localhost:8080/mcp/", Method.PUT);
        HttpResponse response = handler.handle(request);
        assertEquals(405, response.getStatus());
    }

    @Test
    public void testGetRoutesToTransport() {
        HttpResponse mockResponse = createMockResponse(405, "{\"error\":\"Method not allowed\"}");
        when(mockTransport.handleGet(any(HttpRequest.class))).thenReturn(mockResponse);

        HttpRequest request = createTestRequest(
                "http://localhost:8080/mcp/",
                Method.GET,
                new ByteArrayInputStream(new byte[0])
        );
        HttpResponse response = handler.handle(request, null);
        assertEquals(405, response.getStatus());
        // verify(mockTransport).handleGet(request);
    }

    @Test
    public void testDeleteRoutesToTransport() {
        HttpResponse mockResponse = createMockResponse(405, "{\"error\":\"Method not allowed\"}");
        when(mockTransport.handleDelete(any(HttpRequest.class))).thenReturn(mockResponse);

        HttpRequest request = createTestRequest(
                "http://localhost:8080/mcp/",
                Method.DELETE,
                new ByteArrayInputStream(new byte[0])
        );
        HttpResponse response = handler.handle(request, null);
        assertEquals(405, response.getStatus());
        // verify(mockTransport).handleDelete(request);
    }

    @Test
    public void testPostRoutesToTransport() {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"params\":{},\"id\":1}";
        HttpRequest request = createTestRequest(
                "http://localhost:8080/mcp/",
                Method.POST,
                new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8))
        );
        request.getJDiscRequest().headers().add("Accept", "application/json");

        HttpResponse response = handler.handle(request, null);
        assertEquals(200, response.getStatus());
    }

    private HttpResponse createMockResponse(int statusCode, String body) {
        return new HttpResponse(statusCode) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                outputStream.write(body.getBytes(StandardCharsets.UTF_8));
            }
        };
    }
}