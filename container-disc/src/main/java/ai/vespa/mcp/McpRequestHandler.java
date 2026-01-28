package ai.vespa.mcp;

import ai.vespa.mcp.api.McpSpecProvider;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.handler.ContentChannel;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JDisc handler for handling MCP requests.
 * This handler processes HTTP requests directed to the MCP endpoint,
 * and routes them to the appropriate methods in {@link McpHttpTransport}.
 *
 * @author Edvard Dingsør
 * @author Erling Fjelstad
 * @author glebashnik
 * @author bjorncs
 */
public class McpRequestHandler extends ThreadedHttpRequestHandler {
    private static final Logger logger = Logger.getLogger(McpRequestHandler.class.getName());
    private final McpHttpTransport transport;
    private final McpStatelessSyncServer server;

    @Inject
    public McpRequestHandler(Executor executor, Metric metrics, ComponentRegistry<McpSpecProvider> specProviders) {
        super(executor, metrics, true);
        this.transport = new McpHttpTransport();

        var toolSpecs = new ArrayList<McpStatelessServerFeatures.SyncToolSpecification>();
        var resourceSpecs = new ArrayList<McpStatelessServerFeatures.SyncResourceSpecification>();
        var promptSpecs = new ArrayList<McpStatelessServerFeatures.SyncPromptSpecification>();

        for (var mcpSpecProvider : specProviders.allComponents()) {
            logger.fine(() -> "Loading MCP package: " + mcpSpecProvider.getClass().getName());
            toolSpecs.addAll(mcpSpecProvider.getToolSpecs());
            resourceSpecs.addAll(mcpSpecProvider.getResourceSpecs());
            promptSpecs.addAll(mcpSpecProvider.getPromptSpecs());
        }

        logger.fine("Starting Vespa MCP server...");
        server = McpServer.sync(transport)
                .serverInfo("VespaMCP", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(true, true)
                        .prompts(true)
                        .logging()
                        .build())
                .tools(toolSpecs)
                .resources(resourceSpecs)
                .prompts(promptSpecs)
                .build();
        logger.fine("Vespa MCP server started");
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
            logger.log(Level.FINE, "Failed to read request body", e);
            return this.transport.createErrorResponse(400, "Failed to read request body", null);
        }

        logger.fine(() -> "=== RECEIVED REQUEST: " + method + " " + path + " ===");
        byte[] finalBody = body;
        logger.info(() -> "=== BODY: " + new String(finalBody, StandardCharsets.UTF_8) + " ===");

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

    @Override
    public void destroy() {
        try {
            server.closeGracefully().block(); // Will implicitly close transport as well
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error closing MCP server", e);
        }
        super.destroy();
    }
}
