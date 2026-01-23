package ai.vespa.mcp;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Main MCP server application that exposes Vespa search functionality.
 * Configures and starts an MCP server with tools for documentation search, schema inspection, and query execution.
 *
 * @author Erling Fjelstad
 * @author Edvard Dingsør
 */
class JdiscMcpServer implements Closeable {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(JdiscMcpServer.class.getName());

    // MCP transport layer for Vespa request handling
    private final McpHttpTransport transport = new McpHttpTransport();

    // List of MCP tool, resource and prompt specifications for server capabilities
    private final List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecs;
    private final List<McpStatelessServerFeatures.SyncResourceSpecification> resourceSpecs;
    private final List<McpStatelessServerFeatures.SyncPromptSpecification> promptSpecs;

    /**
     * Initializes the MCP server with Vespa tools and starts the server.
     * Injects McpTools to access core functionality.
     */
    @Inject
    public JdiscMcpServer(ComponentRegistry<McpPackage> mcpPackageRegistry) {
        // Register the injected tools, resources, and prompts
        this.toolSpecs = new ArrayList<>();
        this.resourceSpecs = new ArrayList<>();
        this.promptSpecs = new ArrayList<>();

        for (McpPackage mcpPackage : mcpPackageRegistry.allComponents()) {
            logger.info("Loading MCP package: " + mcpPackage.getClass().getName());
            this.toolSpecs.addAll(mcpPackage.getToolSpecs());
            this.resourceSpecs.addAll(mcpPackage.getResourceSpecs());
            this.promptSpecs.addAll(mcpPackage.getPromptSpecs());
        }


        logger.info("Starting Vespa MCP server...");

        // Create the MCP server with tools and capabilities
        McpServer.sync(transport)
                        .serverInfo("VespaMCP", "1.0.0")
                        .capabilities(McpSchema.ServerCapabilities.builder()
                            .tools(true)
                            .resources(true, true)
                            .prompts(true)
                            .logging()
                            .build())
                        .tools(this.toolSpecs)
                        .resources(this.resourceSpecs)
                        .prompts(this.promptSpecs)
                        .build();
        logger.info("Vespa MCP server started");
    }


    public McpHttpTransport getTransport() {
        return this.transport;
    }


    @Override
    public void close() {
        try {
            transport.closeGracefully().block();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error closing MCP transport", e);
        }
    }
}