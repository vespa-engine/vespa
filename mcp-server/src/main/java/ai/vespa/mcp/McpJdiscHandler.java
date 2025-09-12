package ai.vespa.mcp;

import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.component.annotation.Inject;

import io.modelcontextprotocol.spec.McpError;

/**
 * JDisc handler for handling MCP requests.
 * This handler processes HTTP requests directed to the MCP endpoint, and routes them to the appropriate methods in McpHttpTransport .
 * @author Edvard Dingsør
 * @author Erling Fjelstad
*/
public class McpJdiscHandler extends ThreadedHttpRequestHandler{
    private static final Logger logger = Logger.getLogger(McpJdiscHandler.class.getName());

    private final McpHttpTransport  transport;
    
    @Inject
    public McpJdiscHandler(Executor executor,
                    Metric metrics,
                    McpServerComponent  mcpServer) {
        super(executor, metrics, true);
        this.transport = mcpServer.getTransport();
    }

    /**
     * Handles incoming HTTP requests.
     * @see #handle(HttpRequest, ContentChannel) 
     */
    @Override
    public HttpResponse handle(HttpRequest request){
        return handle(request, null);
    }

    /**
     * Handles incoming HTTP requests with a ContentChannel.
     * 
     * @param request The HTTP request to handle.
     * @param channel The ContentChannel associated with the request.
     * @return HttpResponse containing the response to the request.
     */
    @Override
    public HttpResponse handle(HttpRequest request, ContentChannel channel){
        String method = request.getMethod().toString();
        String path = request.getUri().getPath();
        byte[] body = new byte[0];
        try{
            if (request.getData() != null){
                body = request.getData().readAllBytes();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to read request body", e);
            return this.transport.createErrorResponse(400, "Failed to read request body", null);
        }

        logger.info("=== RECEIVED REQUEST: " + method + " " + path + " ===");
        logger.info("=== BODY: " + new String(body, StandardCharsets.UTF_8) + " ===");

        if (!path.startsWith("/mcp/")) {
            return this.transport.createErrorResponse(404, "Not Found", null);
        }

        return switch (method) {
            case "GET" -> this.transport.handleGet(request);
            case "POST" -> this.transport.handlePost(request, body);
            case "DELETE" -> this.transport.handleDelete(request);
            default -> this.transport.createErrorResponse(405, "Only GET, POST, and DELETE requests are allowed", null);
        };
    }
}
