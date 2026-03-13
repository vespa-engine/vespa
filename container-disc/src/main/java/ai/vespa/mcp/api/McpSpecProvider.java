// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.mcp.api;

import java.util.Collection;
import java.util.List;

import static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncPromptSpecification;
import static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncResourceSpecification;
import static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;

/**
 * Interface representing an MCP (Model Context Protocol) specification for tools, resources, prompts, etc.
 * Injected into the {@link ai.vespa.mcp.McpRequestHandler} through a {@link com.yahoo.component.provider.ComponentRegistry}.
 *
 * @author edvardwd
 * @author glebashnik
 * @author bjorncs
 */

public interface McpSpecProvider {
    default Collection<SyncToolSpecification> getToolSpecs() { return List.of(); }
    default Collection<SyncResourceSpecification> getResourceSpecs() { return List.of(); }
    default Collection<SyncPromptSpecification> getPromptSpecs() { return List.of(); }
}
