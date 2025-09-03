package ai.vespa.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;

/**
 * Main MCP server application that exposes Vespa search functionality.
 * Configures and starts an MCP server with tools for documentation search, schema inspection, and query execution.
 *
 * @author Erling Fjelstad
 * @author Edvard Dingsør
 */
public class McpServerComponent extends AbstractComponent{


    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(McpServerComponent.class.getName());

    // MCP transport layer for Vespa request handling
    private final McpHttpTransport transport = new McpHttpTransport();

    // Core Vespa functionality exposed as MCP tools
    private final McpTools vespaTools;

    // List of MCP tool specifications for server capabilities
    private final List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecs;


    /**
     * Initializes the MCP server with Vespa tools and starts the server.
     * Injects McpTools to access core functionality.
     */
    @Inject
    public McpServerComponent(McpTools vespaTools) {
        this.vespaTools = vespaTools;
        // this.transportProvider = new VespaStreamableTransportProvider(Duration.ofSeconds(60));
        // Tools for MCP server
        this.toolSpecs = List.of(
            getDocumentationTool(),
            getSchemasTool(),
            executeQueryTool(),
            readQueryExamplesTool()
        );

        // Resource for MCP server
        var queryExamples = queryExamplesResource();

        // Prompt for MCP server
        var listTools = listToolsPrompt();

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
                        .tools(toolSpecs)
                        .resources(queryExamples)
                        .prompts(listTools)
                        .build();
        logger.info("Vespa MCP server started");
    }


    public McpHttpTransport getTransport() {
        return this.transport;
    }

    private String getRequiredString(Map<String, Object> args, String required) {
        Object val = args.get(required);
        if (!(val instanceof String str) || str.isBlank()) {
            throw new IllegalArgumentException(required + " is required and must be a non-empty string");
        }
        return str;
    }

    /**
     * Converts objects to JSON strings for MCP responses.
     * Used by all MCP tools to format their responses.
     *
     * @param obj the object to convert to JSON
     * @return JSON string representation of the object, or error message if serialization fails.
     */
    private String toJson(Object obj) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return "Error formatting to JSON: " + e.getMessage();
        }
    }

    // ----------------------------------- Tools for MCP server -----------------------------------

    /**
     * Creates MCP tool for searching Vespa's public documentation.
     * Uses semantic search against docs.vespa.ai with natural language queries.
     *
     * @return MCP tool specification for searching documentation.
     */
    private McpStatelessServerFeatures.SyncToolSpecification getDocumentationTool() {
        // Define JSON schema for tool input validation.
        String schema = """
            {
                "type": "object",
                "properties": {
                    "userinput": {
                        "type": "string",
                        "description": "Natural language query to search Vespa documentation.",
                        "minLength": 1
                    }
                },
                "required": ["userinput"],
                "additionalProperties": false,
                "description": "Search Vespa's official documentation using natural language queries."
            }
        """;

        // Define the tool with its name, description, and input schema
        var toolDef = McpSchema.Tool.builder()
            .name("getDocumentation")
            .description("""
                Search Vespa's official documentation using hybrid search.
                
                This tool queries the Vespa documentation at docs.vespa.ai using natural language input.
                It returns relevant documentation sections with titles, content excerpts, and direct URLs to the full articles.
                
                Use this tool to:
                - Find documentation on Vespa features, configuration, and APIs
                - Understand Vespa concepts like schemas, ranking, indexing, and query language
                - Access troubleshooting guides and performance tuning advice
                
                The response includes:
                - "sources": A map of document titles to their full URLs for easy reference
                - "response": The complete response based on the search query
                
                Always cite specific documentation URLs when providing answers based on this tool's results.
                Include URLs inline where information is used, not grouped at the end.
                """)
            .inputSchema(schema)
            .build();

        // Create MCP tool specification with tool definition and execution logic.
        return McpStatelessServerFeatures.SyncToolSpecification.builder()
            .tool(toolDef)
            .callHandler((context, request) -> {
                try {
                    String userinput;
                    try {
                        userinput = getRequiredString(request.arguments(), "userinput");
                    } catch (IllegalArgumentException e) {
                        return new McpSchema.CallToolResult(
                            "{\"error\": \"" + e.getMessage() + "\"}",
                            true
                        );
                    }
                    // Search Vespa's documentation using semantic search.
                    Map<String, Object> documentation = vespaTools.getDocumentation(userinput.trim());

                    // Convert results to JSON for MCP response.
                    String result = toJson(documentation);

                    return new McpSchema.CallToolResult(
                        result,
                        false
                    );
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Documentation search failed", e);
                    return new McpSchema.CallToolResult(
                       "{\"error\": \"Documentation search failed: " + e.getMessage() + "\"}",
                        true
                    );
                }
            })
            .build();
    }

    /**
     * MCP tool for retrieving Vespa schema info.
     * Returns all schemas if no parameters, or selected schemas if 'schemaNames' is provided.
     *
     * @return MCP tool specification for retrieving schema information.
     */
    private McpStatelessServerFeatures.SyncToolSpecification getSchemasTool() {
        // Define JSON schema for tool input validation (no parameters required)
        String schema = """
            {
                "type": "object",
                "properties": {
                    "schemaNames": {
                        "type": "array",
                        "items": { "type": "string" },
                        "description": "Optional list of schema names to fetch. If omitted, all schemas are returned."
                    }
                },
                "required": [],
                "additionalProperties": false,
                "description": "Returns schema information for all or selected schemas in the Vespa application."
            }
        """;

        // Define the tool with its name, description, and input schema
        var defTool = McpSchema.Tool.builder()
            .name("getSchemas")
            .description("""
                Retrieve schema information from the current Vespa application.

                Usage:
                - If no parameters are provided, all schemas are returned.
                - If 'schemaNames' is provided (as a list), only the specified schemas are returned.

                Use this tool to:
                - Understand the data structure and available fields for querying
                - Check field types and indexing configurations
                - Discover available rank profiles and their parameters
                - Plan queries based on the actual schema configuration

                The response provides a complete map of schema information to guide query construction and help understand what data is available for search and ranking.
                """)
            .inputSchema(schema)
            .build();

        // Create MCP tool specification with tool definition and execution logic.
        return McpStatelessServerFeatures.SyncToolSpecification.builder()
            .tool(defTool)
            .callHandler((context, request) -> {
                try {
                    Map<String, Object> arguments = request.arguments();

                    // Returns all schemas if no parameters, or only those specified in 'schemaNames'.
                    Map<String, Object> schemas;
                    if (arguments != null && arguments.containsKey("schemaNames")) {
                        Object schemaNamesObj = arguments.get("schemaNames");
                        if (schemaNamesObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> schemaNames = (List<String>) schemaNamesObj;
                            schemas = vespaTools.getSchemas(schemaNames);
                        } else {
                            return new McpSchema.CallToolResult(
                               "{\"error\": \"Invalid schemaNames parameter, expected an array of schema names\"}",
                                true
                            );
                        }
                    } else {
                        schemas = vespaTools.getSchemas();
                    }

                    // Convert the result to JSON string for MCP response
                    String result = toJson(schemas);

                    return new McpSchema.CallToolResult(
                        result,
                        false
                    );
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Schema retrieval failed", e);
                    return new McpSchema.CallToolResult(
                        "{\"error\": \"Schema retrieval failed: " + e.getMessage() + "\"}",
                        true
                    );
                }
            })
            .build();
    }

    /**
     * Creates MCP tool for executing YQL queries against Vespa.
     * Supports all standard Vespa query parameters and returns search results with metadata.
     * 
     * @return MCP tool specification for executing queries.
     */
    private McpStatelessServerFeatures.SyncToolSpecification executeQueryTool() {
        // Define JSON schema for tool input validation
        String schema = """
            {
                "type": "object",
                "properties": {
                    "yql": {
                        "type": "string",
                        "description": "YQL (Vespa Query Language) query string",
                        "minLength": 1
                    },
                    "queryProfileName": {
                        "type": "string",
                        "description": "Name of the query profile to use",
                        "minLength": 1
                    },
                    "parameters": {
                        "type": "object",
                        "description": "Optional query parameters (hits, ranking, timeout, etc.)",
                        "additionalProperties": true
                    }
                },
                "required": ["yql"],
                "additionalProperties": false,
                "description": "Execute YQL (Vespa Query Language) queries against the Vespa application."
            }
        """;

        // Define the tool with its name, description, and input schema
        var defTool = McpSchema.Tool.builder()
            .name("executeQuery")
            .description("""
                Execute YQL (Vespa Query Language) queries against the Vespa application.

                Before using this tool, ensure you have:
                - Inspected available schemas using getSchemas tool
                - Understood Vespa's query language using getDocumentation tool
                - Read example queries using queryExamples resource
                
                This tool runs queries using Vespa's YQL syntax and returns structured search results.
                It supports the full range of Vespa query capabilities including text search, filtering,
                grouping, sorting, and other features.
                Use 'sources' parameter to restrict search to specific schemas:
                - {"sources": "source1"} - search only news schema
                - {"sources": "source1,source2"} - search multiple schemas
                - Omit sources to search all schemas

                Retrieves the response from the Vespa application based on the provided YQL query.
                """)
            .inputSchema(schema)
            .build();

        // Create MCP tool specification with tool definition and execution logic
        return McpStatelessServerFeatures.SyncToolSpecification.builder()
            .tool(defTool)
            .callHandler((context, request) -> {
                try {
                    Map<String, Object> arguments = request.arguments();

                    // If no arguments provided, return error response.
                    if (arguments == null) {
                        return new McpSchema.CallToolResult(
                            "{\"error\": \"No arguments provided\"}",
                            true
                        );
                    }

                    String yql = (String) arguments.get("yql");

                    // Validate required parameter.
                    if (yql == null || yql.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "{\"error\": \"yql parameter is required and cannot be empty\"}",
                            true
                        );
                    }

                    String queryProfileName = (String) arguments.get("queryProfileName");

                    // Convert optional parameters to string format expected by Vespa.
                    Map<String, String> params = new LinkedHashMap<>();
                    Object parametersObj = arguments.get("parameters");
                    if (parametersObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> inputParams = (Map<String, Object>) parametersObj;
                        for (Map.Entry<String, Object> entry : inputParams.entrySet()) {
                            if (entry.getKey() != null && entry.getValue() != null) {
                                params.put(entry.getKey(), entry.getValue().toString());
                            }
                        }
                    }

                    // Execute the query using McpTools and convert to JSON.
                    String result = toJson(vespaTools.executeQuery(yql, queryProfileName, params));

                    return new McpSchema.CallToolResult(
                        result,
                        false
                    );
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Query execution failed", e);
                    return new McpSchema.CallToolResult(
                        "{\"error\": \"Query execution failed: " + e.getMessage() + "\"}",
                        true
                    );
                }
            })
            .build();
    }

    /**
     * Creates MCP tool for reading query examples from a resource file.
     * @return MCP tool specification for reading query examples
     */
    private McpStatelessServerFeatures.SyncToolSpecification readQueryExamplesTool() {
        String schema = """
            {
                "type": "object",
                "properties": {},
                "required": [],
                "additionalProperties": false,
                "description": "Retrieves example queries that can be used with the executeQuery tool."
            }
        """;

        var toolDef = McpSchema.Tool.builder()
            .name("readQueryExamples")
            .description("""
                Retrieves example queries that can be used with the executeQuery tool.
                Only invoke this tool if you are constructing a query, and have not already read the queryExamples resource.

                This tool provides a set of predefined Vespa queries that demonstrate how to use the executeQuery tool effectively.
                The examples cover various use cases and can be used as a starting point for constructing your own queries.
                """)
            .inputSchema(schema)
            .build();

        return McpStatelessServerFeatures.SyncToolSpecification.builder()
                .tool(toolDef)
                .callHandler((context, request) -> {
                    return new McpSchema.CallToolResult(
                        toJson(vespaTools.readQueryExamples()),
                        false
                    );
                })
                .build();
    }

    // ----------------------------------- Resources for MCP server -----------------------------------

    /**
     * Provides example YQL queries for the executeQuery tool.
     * Reads from a resource file containing example queries.
     *
     * @return MCP resource specification for query examples.
     */
    private McpStatelessServerFeatures.SyncResourceSpecification queryExamplesResource() {
        // Define the resource with its name, URI, description, and MIME type
        var defResource = McpSchema.Resource.builder()
            .name("queryExamples")
            .uri("/resources/queryExamples")
            .description("""
                This resource provides example YQL queries that can be used with the executeQuery tool.

                It uses a Vespa tutorial application to demonstrate various queries and how to translate the command-line query to how the MCP tool can be used.
                Most likely the Vespa application will be different so the example queries are meant for demonstration purposes only.

                For other queries use the getDocumentation tool to find relevant documentation on Vespa's query language.
                """)
            .mimeType("text/plain")
            .build();

        // Create MCP resource specification with resource definition and read handler
        return new McpStatelessServerFeatures.SyncResourceSpecification(
            defResource,
            (context, request) -> {
                try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("queryExamples.txt")) {
                    if (inputStream == null) {
                        throw new FileNotFoundException("Resource not found: queryExamples.txt");
                    }
                    String examples = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

                    return new McpSchema.ReadResourceResult(
                        List.of(new McpSchema.TextResourceContents(
                            "/resources/queryExamples",
                            "text/plain",
                            examples
                        ))
                    );
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to retrieve query examples", e);
                    return new McpSchema.ReadResourceResult(
                        List.of(new McpSchema.TextResourceContents(
                            "/resources/queryExamples",
                            "application/json",
                            "{\"error\": \"Failed to retrieve query examples: " + e.getMessage() + "\"}"
                        ))
                    );
                }
            }
        );
    }

    // ----------------------------------- Prompts for MCP server -----------------------------------

    /**
     * Creates MCP prompt for listing all available tools in the Vespa MCP server.
     * Provides descriptions of each tool and their capabilities.
     *
     * @return MCP prompt specification for listing tools.
     */
    private McpStatelessServerFeatures.SyncPromptSpecification listToolsPrompt() {
        // Create MCP prompt specification for listing tools
        return new McpStatelessServerFeatures.SyncPromptSpecification(
            new McpSchema.Prompt(
                "listTools",
                "List all available tools of the Vespa MCP server and their descriptions.",
                List.of()
            ),
            (context, request) -> {
                try{
                    // Generate summary of all available tools in the MCP server
                    StringBuilder summary = new StringBuilder();
                    summary.append("Summarize the available tools in the Vespa MCP server:\n\n");
                    for (var spec : toolSpecs) {
                        summary.append(spec.tool().name())
                        .append(" - ")
                        .append(spec.tool().description())
                        .append("\n");
                    }

                    // Create prompt message with the summary of tools
                    var messages = List.of(
                        new McpSchema.PromptMessage(
                            McpSchema.Role.ASSISTANT,
                            new McpSchema.TextContent(summary.toString())
                        )
                    );

                    return new McpSchema.GetPromptResult(
                        "Tools available in Vespa MCP server",
                        messages
                    );

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to generate list of tools", e);
                    return new McpSchema.GetPromptResult(
                        "Error generating list of tools",
                        List.of(new McpSchema.PromptMessage(
                            McpSchema.Role.ASSISTANT,
                            new McpSchema.TextContent("Error generating list of tools: " + e.getMessage())
                        ))
                    );
                }
            }
        );
    }

    @Override
    public void deconstruct() {
        logger.info("Deconstructing Vespa MCP server...");
        super.deconstruct();
        logger.info("Vespa MCP server deconstructed");
    }

}