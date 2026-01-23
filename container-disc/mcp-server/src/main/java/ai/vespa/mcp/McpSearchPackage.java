package ai.vespa.mcp;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;


import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.FieldInfo;
import com.yahoo.search.schema.FieldSet;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.tensor.Tensor;
import com.yahoo.search.searchchain.ExecutionFactory;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * Basic MCP (Model Context Protocol) "package" with prewritten tools, resources and prompts related to search.
 *
 * @author edvardwd
 */
public class McpSearchPackage extends AbstractComponent implements McpPackage {
    private static final Logger logger = Logger.getLogger(McpSearchPackage.class.getName());
    private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private final ArrayList<McpStatelessServerFeatures.SyncToolSpecification> toolSpecs;
    private final ArrayList<McpStatelessServerFeatures.SyncResourceSpecification> resourceSpecs;
    private final ArrayList<McpStatelessServerFeatures.SyncPromptSpecification> promptSpecs;

    // Vespa components required for executing queries and reading schema info
    private final ExecutionFactory executionFactory;
    private final Chain<Searcher> searchChain;
    private final SchemaInfo schemaInfo;
    private final CompiledQueryProfileRegistry queryProfileRegistry;

    /**
     * Construct a search package with no tools initialized.
     * Useful when the package is created before Vespa components are available.
     */
    public McpSearchPackage() {
        this.toolSpecs = new ArrayList<>();
        this.resourceSpecs = new ArrayList<>();
        this.promptSpecs = new ArrayList<>();
        this.executionFactory = null;
        this.queryProfileRegistry = null;
        this.searchChain = null;
        this.schemaInfo = null;
    }

    /**
     * Construct a search package and populate tools/resources/prompts using Vespa components.
     */
    @Inject
    public McpSearchPackage(ExecutionFactory executionFactory, CompiledQueryProfileRegistry queryProfileRegistry) {
        this.toolSpecs = new ArrayList<>();
        this.resourceSpecs = new ArrayList<>();
        this.promptSpecs = new ArrayList<>();
        this.executionFactory = executionFactory;
        this.queryProfileRegistry = queryProfileRegistry;
        this.searchChain = executionFactory != null ? executionFactory.searchChainRegistry().getChain("native") : null;
        this.schemaInfo = executionFactory != null ? executionFactory.schemaInfo() : null;

        if (this.executionFactory != null && this.queryProfileRegistry != null) {
            // Tools
            this.toolSpecs.add(getSchemasTool());
            this.toolSpecs.add(executeQueryTool());
            this.toolSpecs.add(readQueryExamplesTool());

            // Resources
            this.resourceSpecs.add(queryExamplesResource());

            // Prompts (depends on toolSpecs for summary)
            this.promptSpecs.add(listToolsPrompt());
        }
    }

    // ---- McpPackage getters ----
    @Override
    public ArrayList<McpStatelessServerFeatures.SyncToolSpecification> getToolSpecs() {
        return toolSpecs;
    }

    @Override
    public ArrayList<McpStatelessServerFeatures.SyncResourceSpecification> getResourceSpecs() {
        return resourceSpecs;
    }

    @Override
    public ArrayList<McpStatelessServerFeatures.SyncPromptSpecification> getPromptSpecs() {
        return promptSpecs;
    }

    // ---- Helper methods copied from JdiscMcpServer ----
    private String getRequiredString(Map<String, Object> args, String required) {
        Object val = args.get(required);
        if (!(val instanceof String str) || str.isBlank()) {
            throw new IllegalArgumentException(required + " is required and must be a non-empty string");
        }
        return (String) val;
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

    /**
     * Type-safe casting helper for Map objects from JSON parsing.
     *
     * @param obj The object to cast.
     * @return The cast object if it is a Map, or null if not.
     */
    @SuppressWarnings("unchecked")
    private static <T> T safeMapCast(Object obj) { return obj instanceof Map ? (T) obj : null; }

    /**
     * Type-safe casting helper for List objects from JSON parsing.
     *
     * @param obj The object to cast.
     * @return The cast object if it is a List, or null if not.
     */
    @SuppressWarnings("unchecked")
    private static <T> T safeListCast(Object obj) { return obj instanceof List ? (T) obj : null; }

    private Map<String, Object> error(String message, Exception e) {
        logger.log(Level.SEVERE, message, e);
        return Map.of("error", message + ": " + e.getMessage());
    }

    // ---------------- Internal Vespa implementations moved from VespaTools ----------------

    /**
     * Retrieves comprehensive schema information from the Vespa application.
     * See {@link #getSchemas(List)}.
     * @return A map of all configured schemas, where each key is the schema name and the value is its metadata.
     */
    private Map<String, Object> getSchemas() {
        List<String> allSchemaNames = new LinkedList<>(schemaInfo.schemas().keySet());
        return getSchemas(allSchemaNames);
    }

    /**
     * Retrieves schema information for a specific list of schema names.
     *
     * @param schemaNames A list of schema names to retrieve.
     * @return A map where each key is the requested schema name and the value is its metadata.
     */
    private Map<String, Object> getSchemas(List<String> schemaNames) {
        logger.info("Trying to retrieve " + schemaNames.size() + " schemas");
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Schema> schemaMap = schemaInfo.schemas();

            // Process each requested schema name.
            int retrievedCount = 0;
            for (String schemaName : schemaNames) {
                Schema schema = schemaMap.get(schemaName);
                if (schema == null) {
                    result.put(schemaName, "Schema not found");
                    continue;
                }
                Map<String, Object> schemaDetails = schemaDetails(schema);
                result.put(schemaName, schemaDetails);
                retrievedCount++;
            }
            logger.info("Retrieved " + retrievedCount + " schemas");
            return result;
        } catch (Exception e) {
            return error("Error fetching schemas", e);
        }
    }

    /**
     * Extracts field or fieldset information from a map of FieldInfo objects.
     * @param fields A map of FieldInfo objects, where each key is the field or fieldset name.
     * @return A map where each key is the field or fieldset name and the value is a map of its details.
     */
    private Map<String, Object> fieldsOrFieldsetsInfo(Map<String, ? extends FieldInfo> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        fields.forEach((name, f) -> {
            Map<String, Object> fieldDetails = new LinkedHashMap<>();
            fieldDetails.put("name", f.name());
            fieldDetails.put("type", f.type().toString());
            fieldDetails.put("isIndex", f.isIndex());
            fieldDetails.put("isAttribute", f.isAttribute());
            fieldDetails.put((f instanceof Field) ? "aliases" : "fieldNames",
                    (f instanceof Field) ? ((Field) f).aliases() : ((FieldSet) f).fieldNames());
            result.put(name, fieldDetails);
        });
        return result;
    }

    /**
     * Converts a Vespa Schema object to a structured map representation.
     * Extracts schema name, fields, rank profiles, and document summaries.
     *
     * @param schema The Vespa Schema object to convert.
     * @return A map containing the schema details, including fields, rank profiles, and summaries
     */
    private Map<String, Object> schemaDetails(Schema schema) {
        try {
            Map<String, Object> schemaDetails = new LinkedHashMap<>();
            schemaDetails.put("name", schema.name());

            // Extract field information with types and indexing configuration
            Map<String, Object> fieldsInfo = fieldsOrFieldsetsInfo(schema.fields());
            schemaDetails.put("fields", fieldsInfo);

            // Extract fieldsets and their configurations
            Map<String, Object> fieldSetsInfo = fieldsOrFieldsetsInfo(schema.fieldSets());
            schemaDetails.put("fieldSets", fieldSetsInfo);

            // Extract rank profile information with inputs and features
            Map<String, Object> rankProfilesInfo = new LinkedHashMap<>();
            schema.rankProfiles().forEach((String profileName, RankProfile profile) -> {
                // Extract rank profile input parameters and their types
                Map<String, String> inputsInfo = new LinkedHashMap<>();
                profile.inputs().forEach((inputName, inputType) -> {
                    inputsInfo.put(inputName, inputType.toString());
                });
                Map<String, Object> profileDetails = new LinkedHashMap<>();
                profileDetails.put("name", profile.name());
                profileDetails.put("hasSummaryFeatures", profile.hasSummaryFeatures());
                profileDetails.put("hasRankFeatures", profile.hasRankFeatures());
                profileDetails.put("useSignificanceModel", profile.useSignificanceModel());
                profileDetails.put("inputs", inputsInfo);
                rankProfilesInfo.put(profileName, profileDetails);
            });
            schemaDetails.put("rankProfiles", rankProfilesInfo);

            // Extract document summary configurations for result presentation
            Map<String, Object> documentSummariesInfo = new LinkedHashMap<>();
            schema.documentSummaries().forEach((summaryName, summary) -> {
                Map<String, Object> summaryDetails = new LinkedHashMap<>();
                summaryDetails.put("name", summary.name());
                summaryDetails.put("isDynamic", summary.isDynamic());

                // Extract summary field configurations
                Map<String, Object> summaryFieldsInfo = new LinkedHashMap<>();
                summary.fields().forEach((fieldName, field) -> {
                    Map<String, String> fieldDetails = new LinkedHashMap<>();
                    fieldDetails.put("name", field.name());
                    fieldDetails.put("type", field.type().asString());
                    summaryFieldsInfo.put(fieldName, fieldDetails);
                });
                summaryDetails.put("fields", summaryFieldsInfo);

                documentSummariesInfo.put(summaryName, summaryDetails);
            });

            schemaDetails.put("documentSummaries", documentSummariesInfo);
            return schemaDetails;
        } catch (Exception e) {
            return error("Error converting schema to map", e);
        }
    }

    /**
     * Executes a YQL query against the Vespa application.
     *
     * @param yql The YQL query string to execute.
     * @param queryProfileName The name of the query profile to use for execution. Defaults to "default".
     * @param params Optional parameters to include in the query request.
     * @return A map containing the query results.
     */
    private Map<String, Object> executeQuery(String yql, String queryProfileName, Map<String, String> params) {
        try {
            if (searchChain == null) {
                return Map.of("error", "No search chain available");
            }

            // Create a new Query object with the provided YQL and parameters.
            Query query = new Query.Builder()
                    .setRequestMap(params != null ? params : Map.of())
                    .setQueryProfile(this.queryProfileRegistry.findQueryProfile(queryProfileName))
                    .setSchemaInfo(schemaInfo)
                    .build();

            // Set the YQL query string and specify query type.
            query.getModel().setQueryString(yql);
            query.getModel().setType(Query.Type.YQL);

            logger.info("Trying to execute query: yql = " + yql + ", params = " + String.valueOf(params));

            // Execute the query through Vespa's search chain.
            Execution execution = executionFactory.newExecution(searchChain);
            Result result = execution.search(query);

            // Fill result with requested information.
            execution.fill(result);
            logger.info("Query executed successfully: " + result.getTotalHitCount() + " total hits found.");

            // Convert Vespa Result to a Map structure.
            return resultToMap(result);
        } catch (Exception e) {
            return error("Error executing query", e);
        }
    }

    /**
     * Converts a Vespa Result object to a Map structure.
     *
     * @param result The Vespa Result object to convert.
     * @return A Map representation of the result.
     */
    private Map<String, Object> resultToMap(Result result) {
        try {
            // Create root structure matching Vespa's JSON API format.
            Map<String, Object> rootWrapper = new LinkedHashMap<>();
            Map<String, Object> root = new LinkedHashMap<>();
            rootWrapper.put("root", root);

            // Root metadata - query ID and relevance.
            root.put("id", result.hits().getId() != null ? result.hits().getId().toString() : null);
            root.put("relevance", result.hits().getRelevance() != null ? result.hits().getRelevance().getScore() : null);

            // Root fields - total hit count.
            root.put("fields", Map.of("totalCount", result.getTotalHitCount()));

            // Coverage information - search completeness metrics.
            Coverage cov = result.getCoverage(false);
            if (cov != null) {
                Map<String, Object> coverage = new LinkedHashMap<>();
                coverage.put("coverage", cov.getResultPercentage());
                coverage.put("documents", cov.getDocs());
                coverage.put("full", cov.getFull());
                coverage.put("nodes", cov.getNodes());
                coverage.put("results", cov.getResultSets());
                coverage.put("resultsFull", cov.getFullResultSets());
                root.put("coverage", coverage);
            }

            // Children - individual search result hits.
            List<Map<String, Object>> children = new LinkedList<>();
            for (Iterator<Hit> hitIterator = result.hits().deepIterator(); hitIterator.hasNext(); ) {
                Hit hit = hitIterator.next();
                if (hit.isMeta()) continue; // Skip meta hits.
                Map<String, Object> child = hitToMap(hit);
                children.add(child);
            }
            root.put("children", children);

            return rootWrapper;
        } catch (Exception e) {
            return error("JSON conversion failed", e);
        }
    }

    /**
     * Converts a Vespa Hit object to a Map structure.
     *
     * @param hit The Vespa Hit object to convert.
     * @return A Map representation of the hit.
     */
    private Map<String, Object> hitToMap(Hit hit) {
        Map<String, Object> hitMap = new LinkedHashMap<>();
        hitMap.put("id", hit.getId() != null ? hit.getId().toString() : null);
        hitMap.put("relevance", hit.getRelevance() != null ? hit.getRelevance().getScore() : null);
        hitMap.put("source", hit.getSource() != null ? hit.getSource() : null);

        // Extract all document fields from the hit.
        Map<String, Object> fields = new LinkedHashMap<>();
        hit.forEachField((fieldName, fieldValue) -> {
            fields.put(fieldName, convertFieldValue(fieldValue));
        });
        hitMap.put("fields", fields);
        return hitMap;
    }

    /**
     * Converts field values to JSON-compatible types.
     * Handles primitive types, collections, and complex objects safely.
     *
     * @param fieldValue The value of the field to convert.
     * @return The converted value suitable for JSON serialization.
     */
    private Object convertFieldValue(Object fieldValue) {
        try{
            if (fieldValue == null) return null;
            if (fieldValue instanceof Tensor tensor) {
                Map<String, Object> tensorNode = new LinkedHashMap<>();
                tensorNode.put("type", tensor.type().toString());

                // Convert tensor cells to Map format.
                Map<String, Object> cells = new LinkedHashMap<>();
                for (var entry : tensor.cells().entrySet()) {
                    cells.put(entry.getKey().toString(), entry.getValue());
                }
                tensorNode.put("cells", cells);
                return tensorNode;
            }
            // Primitive and complex JSON types.
            if (fieldValue instanceof String || fieldValue instanceof Number ||
                    fieldValue instanceof Boolean || fieldValue instanceof Collection ||
                    fieldValue instanceof Map) return fieldValue;

            // Everything else - convert to string representation.
            return fieldValue.toString();
        } catch (Exception e) {
            return fieldValue.toString(); // Fallback to string conversion on error
        }
    }

    /**
     * Reads query examples from a resource file and returns them as a string.
     *
     * @return A string containing the query examples, or an error message if reading fails.
     */
    private String readQueryExamples() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("queryExamples.txt")){
            if (inputStream == null) {
                logger.log(Level.SEVERE, "Query examples resource not found");
                return "Query examples resource not found";
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to read query examples resource: " + e.getMessage(), e);
            return "Error reading query examples: " + e.getMessage();
        }
    }

    // ----------------------------------- Tools for MCP search package -----------------------------------


    /**
     * MCP tool for retrieving Vespa schema info.
     * Returns all schemas if no parameters, or selected schemas if 'schemaNames' is provided.
     *
     * @return MCP tool specification for retrieving schema information.
     */
    private McpStatelessServerFeatures.SyncToolSpecification getSchemasTool() {
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

        return McpStatelessServerFeatures.SyncToolSpecification.builder()
            .tool(defTool)
            .callHandler((context, request) -> {
                try {
                    Map<String, Object> arguments = request.arguments();
                    Map<String, Object> schemas;
                    if (arguments != null && arguments.containsKey("schemaNames")) {
                        Object schemaNamesObj = arguments.get("schemaNames");
                        if (schemaNamesObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> schemaNames = (List<String>) schemaNamesObj;
                            schemas = getSchemas(schemaNames);
                        } else {
                            return new McpSchema.CallToolResult(
                               "{\"error\": \"Invalid schemaNames parameter, expected an array of schema names\"}",
                                true
                            );
                        }
                    } else {
                        schemas = getSchemas();
                    }
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

        return McpStatelessServerFeatures.SyncToolSpecification.builder()
            .tool(defTool)
            .callHandler((context, request) -> {
                try {
                    Map<String, Object> arguments = request.arguments();
                    if (arguments == null) {
                        return new McpSchema.CallToolResult(
                            "{\"error\": \"No arguments provided\"}",
                            true
                        );
                    }

                    String yql = (String) arguments.get("yql");
                    if (yql == null || yql.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "{\"error\": \"yql parameter is required and cannot be empty\"}",
                            true
                        );
                    }

                    String queryProfileName = (String) arguments.get("queryProfileName");

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

                    String result = toJson(executeQuery(yql, queryProfileName, params));

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
                        toJson(readQueryExamples()),
                        false
                    );
                })
                .build();
    }

    // ----------------------------------- Resources for MCP search package -----------------------------------

    /**
     * Provides example YQL queries for the executeQuery tool.
     * Reads from a resource file containing example queries.
     *
     * @return MCP resource specification for query examples.
     */
    private McpStatelessServerFeatures.SyncResourceSpecification queryExamplesResource() {
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

    // ----------------------------------- Prompts for MCP search package -----------------------------------

    /**
     * Creates MCP prompt for listing all available tools in this package.
     * Provides descriptions of each tool and their capabilities.
     *
     * @return MCP prompt specification for listing tools.
     */
    private McpStatelessServerFeatures.SyncPromptSpecification listToolsPrompt() {
        return new McpStatelessServerFeatures.SyncPromptSpecification(
            new McpSchema.Prompt(
                "listTools",
                "List all available tools of the Vespa MCP server and their descriptions.",
                List.of()
            ),
            (context, request) -> {
                try{
                    StringBuilder summary = new StringBuilder();
                    summary.append("Summarize the available tools in the Vespa MCP server:\n\n");
                    for (var spec : toolSpecs) {
                        summary.append(spec.tool().name())
                               .append(" - ")
                               .append(spec.tool().description())
                               .append("\n");
                    }

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
}
