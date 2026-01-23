package ai.vespa.mcp;

import io.modelcontextprotocol.server.McpStatelessServerFeatures;

import java.util.ArrayList;

/**
 * Interface representing an MCP (Model Context Protocol) "package".
 * Used for grouping tool, resource and prompt specs to ship within an MCP server.
 *
 * @author edvardwd
 */

public interface McpPackage {
    default  ArrayList<McpStatelessServerFeatures.SyncToolSpecification> getToolSpecs() {
        return new ArrayList<>();
    }
    default ArrayList<McpStatelessServerFeatures.SyncResourceSpecification> getResourceSpecs() {
        return new ArrayList<>();
    }
    default ArrayList<McpStatelessServerFeatures.SyncPromptSpecification> getPromptSpecs() {
        return new ArrayList<>();
    }
}
