// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.mcp;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Searcher;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.ExecutionFactory;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.searchchain.SearchChainRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the McpSearchPackage class.
 *
 * @author Edvard Dingsør
 */
public class McpSearchPackageTest {

    private McpSearchSpecProvider mcpSearchPackage;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        ExecutionFactory mockExecutionFactory = Mockito.mock(ExecutionFactory.class);
        CompiledQueryProfileRegistry mockQueryProfileRegistry = Mockito.mock(CompiledQueryProfileRegistry.class);
        SchemaInfo mockSchemaInfo = Mockito.mock(SchemaInfo.class);

        // Mock the search chain registry and chain
        SearchChainRegistry mockSearchChainRegistry = Mockito.mock(SearchChainRegistry.class);
        Chain<Searcher> mockSearchChain = Mockito.mock(Chain.class);

        when(mockExecutionFactory.schemaInfo()).thenReturn(mockSchemaInfo);
        when(mockExecutionFactory.searchChainRegistry()).thenReturn(mockSearchChainRegistry);
        when(mockSearchChainRegistry.getChain("native")).thenReturn(mockSearchChain);
        when(mockSchemaInfo.schemas()).thenReturn(Map.of());

        mcpSearchPackage = new McpSearchSpecProvider(mockExecutionFactory, mockQueryProfileRegistry);
    }

    @Test
    public void testToolSpecsAreRegistered() {
        var toolSpecs = mcpSearchPackage.getToolSpecs();
        assertNotNull(toolSpecs);
        assertEquals(3, toolSpecs.size());

        // Verify expected tools are registered
        var toolNames = toolSpecs.stream().map(spec -> spec.tool().name()).toList();
        assertTrue(toolNames.contains("getSchemas"));
        assertTrue(toolNames.contains("executeQuery"));
        assertTrue(toolNames.contains("readQueryExamples"));
    }

    @Test
    public void testResourceSpecsAreRegistered() {
        var resourceSpecs = mcpSearchPackage.getResourceSpecs();
        assertNotNull(resourceSpecs);
        assertEquals(1, resourceSpecs.size());
    }

    @Test
    public void testPromptSpecsAreRegistered() {
        var promptSpecs = mcpSearchPackage.getPromptSpecs();
        assertNotNull(promptSpecs);
        assertEquals(1, promptSpecs.size());
    }
}
