package ai.vespa.mcp;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Searcher;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.ExecutionFactory;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.searchchain.SearchChainRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the McpTools class.
 *
 * @author Edvard Dingsør
 */
public class McpToolsTest {

    private McpTools mcpTools;

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

        mcpTools = new McpTools(mockExecutionFactory, mockQueryProfileRegistry);
    }

    @Test
    public void testGetSchemasReturnsEmptyMapWhenNoSchemas() {
        Map<String, Object> result = mcpTools.getSchemas();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testReadQueryExamplesReturnsString() {
        String examples = mcpTools.readQueryExamples();
        System.out.println(examples);
        assertNotNull(examples);
        assertFalse(examples.isEmpty());
    }

    @Test
    public void testGetDocumentationWithValidInput() {
        // This tests the HTTP client functionality
        Map<String, Object> result = mcpTools.getDocumentation("vespa search");
        assertNotNull(result);
    }

    @Test
    public void testGetSchemasWithSpecificSchemaNames() {
        List<String> schemaNames = List.of("nonexistent_schema");
        Map<String, Object> result = mcpTools.getSchemas(schemaNames);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Schema not found", result.get("nonexistent_schema"));
    }
}