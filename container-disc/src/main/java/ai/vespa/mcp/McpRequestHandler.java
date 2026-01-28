// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.mcp;

import ai.vespa.mcp.api.McpSpecProvider;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiRequestHandler;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
public class McpRequestHandler extends RestApiRequestHandler<McpRequestHandler> {

    private static final Logger logger = Logger.getLogger(McpRequestHandler.class.getName());

    private final McpHttpTransport transport;
    private final McpStatelessSyncServer server;

    @Inject
    public McpRequestHandler(ThreadedHttpRequestHandler.Context context, ComponentRegistry<McpSpecProvider> specProviders) {
        super(context, McpRequestHandler::createRestApiDefinition);
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

    private static RestApi createRestApiDefinition(McpRequestHandler self) {
        return RestApi.builder()
                .addRoute(RestApi.route("/mcp/{*}")
                        .post(self::handleMcpPost)
                        .defaultHandler(self::handleMethodNotAllowed))
                .build();
    }

    private HttpResponse handleMcpPost(RestApi.RequestContext context) {
        byte[] body;
        try {
            body = context.requestContentOrThrow().content().readAllBytes();
        } catch (IOException e) {
            logger.log(Level.FINE, "Failed to read request body", e);
            return transport.createErrorResponse(500, "Failed to read request body", null);
        }

        logger.fine(() -> "=== RECEIVED REQUEST: POST " + context.request().getUri().getPath() + " ===");
        logger.info(() -> "=== BODY: " + new String(body, StandardCharsets.UTF_8) + " ===");

        return transport.handlePost(context.request(), body);
    }

    private HttpResponse handleMethodNotAllowed(RestApi.RequestContext context) {
        return transport.createErrorResponse(405, "Only POST requests are allowed", null);
    }

    @Override
    protected void destroy() {
        try {
            server.closeGracefully().block();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error closing MCP server", e);
        }
        super.destroy();
    }
}
