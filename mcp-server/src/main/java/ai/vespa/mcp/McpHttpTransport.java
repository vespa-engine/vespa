package ai.vespa.mcp;

import java.io.IOException;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;


import io.modelcontextprotocol.server.DefaultMcpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.spec.*;

import reactor.core.publisher.Mono;

/**
 * McpHttpTransport is a transport implementation for a stateless MCP server.
 * It processes HTTP requests, extracts the context, and routes the requests to the appropriate handler.
 * It also provides methods for creating HTTP responses and handling errors.
 * @author Edvard Dingsør
 */
public class McpHttpTransport implements McpStatelessServerTransport {

    private static final Logger logger = Logger.getLogger(McpHttpTransport.class.getName());

    private final ObjectMapper mapper;
    private McpStatelessServerHandler mcpHandler;
    private final McpTransportContextExtractor<HttpRequest> contextExtractor;
    private volatile boolean isClosing = false;

    public McpHttpTransport() {
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
                    headers().add("Access-Control-Allow-Origin", "*");
                    outputStream.write(jsonText.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }
            }
        };
    }

    /**
     * Creates an error response with the given status code and error message. Logs the exception.
     * @param statusCode the HTTP status code for the error response
     * @param errorMsg the error message to include in the response
     * @param e the exception that caused the error (can be null)
     * @return an HttpResponse object with the specified status code and error message in JSON format
     */
    public HttpResponse createErrorResponse(int statusCode, String errorMsg, Exception e) {
        if (e != null) {
            logger.log(Level.SEVERE, errorMsg, e);
        }
        return createHttpResponse(statusCode, errorMsg);
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
        if (accept == null || !accept.contains("application/json")) {
            return createErrorResponse(400, "application/json must be in the Accept header", null);
        }
        if (this.isClosing) {
            logger.log(Level.SEVERE, "POST request received while transport is closing");
            return createErrorResponse(503, "Transport is closing, no further requests will be accepted", null);
        }
        McpTransportContext context = contextExtractor.extract(request, new DefaultMcpTransportContext());

        try {
            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(mapper, new String(requestBody, StandardCharsets.UTF_8));

            if (message instanceof McpSchema.JSONRPCRequest jsonrpcRequest) {
                // Hacky workaround since there is no default handler for logging/setLevel (at this time).
                // TODO: fix this properly
                if ("logging/setLevel".equals(jsonrpcRequest.method())) {
                    logger.info("Handling logging/setLevel request directly");
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
                    return createErrorResponse(500, "Failed to handle request: " + e.getMessage(), e);
                }
            }
            else if (message instanceof McpSchema.JSONRPCNotification jsonrpcNotification) {
                try {
                    this.mcpHandler.handleNotification(context, jsonrpcNotification)
                            .contextWrite(ctx -> ctx.put(McpTransportContext.KEY, context))
                            .block();
                    return createHttpResponse(202, null);
            } catch (Exception e) {
                    return createErrorResponse(500, "Failed to handle notification: " + e.getMessage(), e);
                }
            }
            else {
                logger.log(Level.SEVERE, "Message type must be either JSONRPCRequest or JSONRPCNotification, but was: " + message.getClass().getName());
                return createErrorResponse(400, "The server only accepts jsonrpc requests and notifications", null);
            }
        }
        catch (IllegalArgumentException | IOException e) {
            return createErrorResponse(400, "Failed to deserialize message: " + e.getMessage(), e);
        }
        catch (Exception e) {
            return createErrorResponse(500, "Unexpected error handling message: " + e.getMessage(), e);
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