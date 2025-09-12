// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.mcp;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest.Method;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static com.yahoo.container.jdisc.HttpRequest.createTestRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

/**
 * Unit tests for the McpHttpTransport class.
 */
public class McpHttpTransportTest {

    private McpHttpTransport transport;
    private McpStatelessServerHandler mockHandler;

    @BeforeEach
    public void setUp() {
        transport = new McpHttpTransport();
        mockHandler = Mockito.mock(McpStatelessServerHandler.class);
        transport.setMcpHandler(mockHandler);
    }


    @Test
    public void testUnsupportedRequest() {
        HttpRequest getRequest = createTestRequest("http://localhost:8080/mcp/", Method.GET);
        HttpResponse getResponse = transport.handleGet(getRequest);
        assertEquals(405, getResponse.getStatus());

        HttpRequest deleteRequest = createTestRequest("http://localhost:8080/mcp/", Method.DELETE);
        HttpResponse deleteResponse = transport.handleDelete(deleteRequest);
        assertEquals(405, deleteResponse.getStatus());
    }

    @Test
    public void testHandlePostWithMissingAcceptHeader() {
        // Create a POST request without an Accept header
        HttpRequest request = createTestRequest("http://localhost:8080/mcp/", Method.POST);
        HttpResponse response = transport.handlePost(request, new byte[0]);
        assertEquals(400, response.getStatus());
        
        // Verify the response body contains the expected error message
        String responseBody = renderResponse(response);
        assertTrue(responseBody.contains("application/json must be in the Accept header"));
    }

    @Test
    public void testHandlePostWithInvalidJson() {
        // Create a POST request with an Accept header but invalid JSON body
        HttpRequest request = createTestRequest(
                "http://localhost:8080/mcp/",
                Method.POST,
                new ByteArrayInputStream("invalid json".getBytes(StandardCharsets.UTF_8)),
                java.util.Map.of("Accept", "application/json")
        );
        
        HttpResponse response = transport.handlePost(request, "invalid json".getBytes(StandardCharsets.UTF_8));

        // Verify that a 400 Bad Request response with an error message is returned
        assertEquals(400, response.getStatus());
        String responseBody = renderResponse(response);
        assertTrue(responseBody != null && !responseBody.isEmpty());
    }

    @Test
    public void testHandlePostWithValidJsonRpcRequest() throws Exception {
        // Create a POST request with an Accept header and a simple JSON-RPC request body
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":\"123\",\"method\":\"tools/list\",\"params\":{}}";
        HttpRequest request = createTestRequest(
                "http://localhost:8080/mcp/",
                Method.POST,
                new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8))
        );

        request.getJDiscRequest().headers().add("Content-Type", "application/json");
        request.getJDiscRequest().headers().add("Accept", "application/json");

        McpSchema.JSONRPCResponse jsonRpcResponse = new McpSchema.JSONRPCResponse(
                "2.0",
                "123",
                new HashMap<>(),
                null
        );

        Mockito.doReturn(Mono.just(jsonRpcResponse))
                .when(mockHandler)
                .handleRequest(any(McpTransportContext.class), any(McpSchema.JSONRPCRequest.class));

        HttpResponse response = transport.handlePost(request, requestBody.getBytes(StandardCharsets.UTF_8));
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testCreateErrorResponse() {
        // Create an error response
        HttpResponse response = transport.createErrorResponse(400, "Test error message", null);
        
        // Verify that a 400 Bad Request response with the error message is returned
        assertEquals(400, response.getStatus());
        String responseBody = renderResponse(response);
        assertTrue(responseBody.contains("Test error message"));
    }

    /**
     * Helper method to render an HttpResponse to a String.
     */
    private String renderResponse(HttpResponse response) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            response.render(out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to render response", e);
        }
    }
}