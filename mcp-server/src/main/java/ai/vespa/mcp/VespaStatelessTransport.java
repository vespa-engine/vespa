package ai.vespa.mcp;

import java.io.IOException;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;


import io.modelcontextprotocol.server.DefaultMcpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.spec.*;

import reactor.core.publisher.Mono;

import org.apache.log4j.Logger;

/**
 * VespaStatelessTransport is a transport implementation for a stateless MCP server.
 * It processes HTTP requests, extracts the context, and routes the requests to the appropriate handler.
 * It also provides methods for creating HTTP responses and handling errors.
 * @author Edvard Dingsør
 */
@SuppressWarnings("deprecation")
public class VespaStatelessTransport implements McpStatelessServerTransport {

    private static final Logger log = Logger.getLogger(VespaStatelessTransport.class.getName());

    private final ObjectMapper mapper;
    private McpStatelessServerHandler mcpHandler;
    private final McpTransportContextExtractor<HttpRequest> contextExtractor;
    private volatile boolean isClosing = false;

    public VespaStatelessTransport() {
        this.mapper = new ObjectMapper();
        this.contextExtractor = (request, context) -> context; // Change later??
    }

    @Override
    public void setMcpHandler(McpStatelessServerHandler mcpHandler) {
        this.mcpHandler = mcpHandler;
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> this.isClosing = true);
    }

    /**
     * Creates an HTTP response with the given status code and JSON text.
     * @param statusCode the HTTP status code for the response
     * @param jsonText the JSON text to include in the response body, or null if no body is needed
     * @return an HttpResponse object with the specified status code and body
     */
    private HttpResponse createHttpResponse(int statusCode, String jsonText) {
        return new HttpResponse(statusCode) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                if (jsonText != null) {
                    headers().add("Content-Type", "application/json");
                    headers().add("Access-Control-Allow-Origin", "*"); // TODO: change this?
                    outputStream.write(jsonText.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }
            }
        };
    }

    /**
     * Creates an error response with the given status code and error message.
     * @param statusCode the HTTP status code for the error response
     * @param error the McpError object containing the error details
     * @return an HttpResponse object with the specified status code and error message in JSON format
     */
    public HttpResponse createErrorResponse(int statusCode, McpError error) {
        try {
            String jsonError = mapper.writeValueAsString(error);
            return createHttpResponse(statusCode, jsonError);
        } catch (IOException e) {
            log.error("Failed to serialize error response: " + e.getMessage(), e);
            return createHttpResponse(500, "{\"error\":\"Internal Server Error\"}");
        }
    }

    /**
     * Creates an error response and logs the exception.
     * @see #createErrorResponse(int, McpError)
     */
    public HttpResponse createErrorResponse(int statusCode, McpError error, Exception e) {
        log.error(error.getMessage(), e);
        return createErrorResponse(statusCode, error);
    }

    /**
     * Handles GET requests, which are not supported by this transport.
     * @param request The HTTP request to handle.
     * @return  a 405 Method Not Allowed response.
     */
    public HttpResponse handleGet(HttpRequest request) {
        return createHttpResponse(405, null);
    }

    /**
     * Handles POST requests by extracting the context, deserializing the request body,
     * and routing the request to the appropriate handler.
     * @param request The HTTP request to handle.
     * @param requestBody The body of the HTTP request, expected to be a JSON-RPC message.
     * @return an HttpResponse containing the result of processing the request.
     */
    public HttpResponse handlePost(HttpRequest request, byte[] requestBody) {
        String accept = request.getHeader("Accept");
        if (accept == null || !accept.contains("application/json") || !accept.contains("text/event-stream")) {
            return createErrorResponse(400, new McpError("Both application/json and text/event-stream must be in the Accept header"));
        }
        if (this.isClosing) {
            log.error("POST request received while transport is closing");
            return createErrorResponse(503, new McpError("Transport is closing, no further requests will be accepted"));
        }
        McpTransportContext context = contextExtractor.extract(request, new DefaultMcpTransportContext());

        try {
            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(mapper, new String(requestBody, StandardCharsets.UTF_8));

            if (message instanceof McpSchema.JSONRPCRequest jsonrpcRequest) {
                // Hacky workaround since there is no default handler for logging/setLevel (at this time).
                // TODO: fix this properly
                if ("logging/setLevel".equals(jsonrpcRequest.method())) {
                    log.info("Handling logging/setLevel request directly");
                    McpSchema.JSONRPCResponse response = new McpSchema.JSONRPCResponse(
                            "2.0",
                            jsonrpcRequest.id(),
                            new HashMap<>(),  // Empty success response
                            null
                    );
                    return createHttpResponse(200, mapper.writeValueAsString(response));
                }
                try {
                    McpSchema.JSONRPCResponse jsonrpcResponse = this.mcpHandler
                            .handleRequest(context, jsonrpcRequest)
                            .contextWrite(ctx -> ctx.put(McpTransportContext.KEY, context))
                            .block();
                    return createHttpResponse(200, mapper.writeValueAsString(jsonrpcResponse));
                } catch (Exception e) {
                    return createErrorResponse(500, new McpError("Failed to handle request: " + e.getMessage()), e);
                }
            }
            else if (message instanceof McpSchema.JSONRPCNotification jsonrpcNotification) {
                try {
                    this.mcpHandler.handleNotification(context, jsonrpcNotification)
                            .contextWrite(ctx -> ctx.put(McpTransportContext.KEY, context))
                            .block();
                    return createHttpResponse(202, null);
            } catch (Exception e) {
                    return createErrorResponse(500, new McpError("Failed to handle notification: " + e.getMessage()), e);
                }
            }
            else {
                log.error("Message type must be either JSONRPCRequest or JSONRPCNotification, but was: " + message.getClass().getName());
                return createErrorResponse(400, new McpError("The server only accepts jsonrpc requests and notifications"));
            }
        }
        catch (IllegalArgumentException | IOException e) {
            return createErrorResponse(400, new McpError("Failed to deserialize message: " + e.getMessage()), e);
        }
        catch (Exception e) {
            return createErrorResponse(500, new McpError("Unexpected error handling message: " + e.getMessage()), e);
        }

    }

    /**
     * Handles DELETE requests, which are not supported by this transport.
     * @param request The HTTP request to handle.
     * @return a 405 Method Not Allowed response.
     */
    public HttpResponse handleDelete(HttpRequest request) {
        return createHttpResponse(405, null);
    }

}