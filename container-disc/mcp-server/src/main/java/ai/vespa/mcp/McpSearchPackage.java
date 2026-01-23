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

//    /**
//     * Construct a search package with no tools initialized.
//     * Useful when the package is created before Vespa components are available.
//     */
//    public McpSearchPackage() {
//        this.toolSpecs = new ArrayList<>();
//        this.resourceSpecs = new ArrayList<>();
//        this.promptSpecs = new ArrayList<>();
//        this.executionFactory = null;
//        this.queryProfileRegistry = null;
//        this.searchChain = null;
//        this.schemaInfo = null;
//    }

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

    // ---- Helpers ----
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

    /** Extracts field or fieldset information from a map of FieldInfo objects. */
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

    /** Converts a Vespa Schema object to a structured map representation. */
    private Map<String, Object> schemaDetails(Schema schema) {
        Map<String, Object> schemaDetails = new LinkedHashMap<>();
        schemaDetails.put("name", schema.name());
        schemaDetails.put("fields", fieldsOrFieldsetsInfo(schema.fields()));
        schemaDetails.put("fieldSets", fieldsOrFieldsetsInfo(schema.fieldSets()));

        Map<String, Object> rankProfilesInfo = new LinkedHashMap<>();
        schema.rankProfiles().forEach((profileName, profile) -> {
            Map<String, String> inputsInfo = new LinkedHashMap<>();
            profile.inputs().forEach((inputName, inputType) -> inputsInfo.put(inputName, inputType.toString()));
            Map<String, Object> profileDetails = new LinkedHashMap<>();
            profileDetails.put("name", profile.name());
            profileDetails.put("hasSummaryFeatures", profile.hasSummaryFeatures());
            profileDetails.put("hasRankFeatures", profile.hasRankFeatures());
            profileDetails.put("useSignificanceModel", profile.useSignificanceModel());
            profileDetails.put("inputs", inputsInfo);
            rankProfilesInfo.put(profileName, profileDetails);
        });
        schemaDetails.put("rankProfiles", rankProfilesInfo);

        Map<String, Object> documentSummariesInfo = new LinkedHashMap<>();
        schema.documentSummaries().forEach((summaryName, summary) -> {
            Map<String, Object> summaryDetails = new LinkedHashMap<>();
            summaryDetails.put("name", summary.name());
            summaryDetails.put("isDynamic", summary.isDynamic());
            Map<String, Object> summaryFieldsInfo = new LinkedHashMap<>();
            summary.fields().forEach((fieldName, field) -> {
                summaryFieldsInfo.put(fieldName, Map.of("name", field.name(), "type", field.type().asString()));
            });
            summaryDetails.put("fields", summaryFieldsInfo);
            documentSummariesInfo.put(summaryName, summaryDetails);
        });
        schemaDetails.put("documentSummaries", documentSummariesInfo);
        return schemaDetails;
    }

    /** Converts a Vespa Result object to a Map structure. */
    private Map<String, Object> resultToMap(Result result) {
        Map<String, Object> rootWrapper = new LinkedHashMap<>();
        Map<String, Object> root = new LinkedHashMap<>();
        rootWrapper.put("root", root);
        root.put("id", result.hits().getId() != null ? result.hits().getId().toString() : null);
        root.put("relevance", result.hits().getRelevance() != null ? result.hits().getRelevance().getScore() : null);
        root.put("fields", Map.of("totalCount", result.getTotalHitCount()));

        Coverage cov = result.getCoverage(false);
        if (cov != null) {
            root.put("coverage", Map.of(
                "coverage", cov.getResultPercentage(),
                "documents", cov.getDocs(),
                "full", cov.getFull(),
                "nodes", cov.getNodes(),
                "results", cov.getResultSets(),
                "resultsFull", cov.getFullResultSets()
            ));
        }

        List<Map<String, Object>> children = new LinkedList<>();
        for (Iterator<Hit> it = result.hits().deepIterator(); it.hasNext(); ) {
            Hit hit = it.next();
            if (!hit.isMeta()) children.add(hitToMap(hit));
        }
        root.put("children", children);
        return rootWrapper;
    }

    /** Converts a Vespa Hit object to a Map structure. */
    private Map<String, Object> hitToMap(Hit hit) {
        Map<String, Object> hitMap = new LinkedHashMap<>();
        hitMap.put("id", hit.getId() != null ? hit.getId().toString() : null);
        hitMap.put("relevance", hit.getRelevance() != null ? hit.getRelevance().getScore() : null);
        hitMap.put("source", hit.getSource());
        Map<String, Object> fields = new LinkedHashMap<>();
        hit.forEachField((fieldName, fieldValue) -> fields.put(fieldName, convertFieldValue(fieldValue)));
        hitMap.put("fields", fields);
        return hitMap;
    }

    /** Converts field values to JSON-compatible types. */
    private Object convertFieldValue(Object fieldValue) {
        if (fieldValue == null) return null;
        if (fieldValue instanceof Tensor tensor) {
            Map<String, Object> cells = new LinkedHashMap<>();
            tensor.cells().forEach((key, value) -> cells.put(key.toString(), value));
            return Map.of("type", tensor.type().toString(), "cells", cells);
        }
        if (fieldValue instanceof String || fieldValue instanceof Number ||
                fieldValue instanceof Boolean || fieldValue instanceof Collection ||
                fieldValue instanceof Map) return fieldValue;
        return fieldValue.toString();
    }

    // ----------------------------------- Tools -----------------------------------

    /**
     * MCP tool for retrieving Vespa schema info.
     * Returns all schemas if no parameters, or selected schemas if 'schemaNames' is provided.
     */
    private McpStatelessServerFeatures.SyncToolSpecification getSchemasTool() {
        String inputSchema = """
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
                "additionalProperties": false
            }
        """;

        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name("getSchemas")
            .description("""
                Retrieve schema information from the current Vespa application.
                Use this to understand data structure, field types, rank profiles, and plan queries.""")
            .inputSchema(inputSchema)
            .build();

        return McpStatelessServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((context, request) -> {
                try {
                    List<String> schemaNames;
                    Map<String, Object> arguments = request.arguments();
                    if (arguments != null && arguments.containsKey("schemaNames")) {
                        Object obj = arguments.get("schemaNames");
                        if (!(obj instanceof List)) {
                            return new McpSchema.CallToolResult("{\"error\": \"schemaNames must be an array\"}", true);
                        }
                        @SuppressWarnings("unchecked")
                        List<String> names = (List<String>) obj;
                        schemaNames = names;
                    } else {
                        schemaNames = new LinkedList<>(schemaInfo.schemas().keySet());
                    }

                    logger.info("Retrieving " + schemaNames.size() + " schemas");
                    Map<String, Object> result = new LinkedHashMap<>();
                    Map<String, Schema> schemaMap = schemaInfo.schemas();
                    for (String name : schemaNames) {
                        Schema schema = schemaMap.get(name);
                        result.put(name, schema != null ? schemaDetails(schema) : "Schema not found");
                    }
                    return new McpSchema.CallToolResult(toJson(result), false);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Schema retrieval failed", e);
                    return new McpSchema.CallToolResult("{\"error\": \"" + e.getMessage() + "\"}", true);
                }
            })
            .build();
    }

    /**
     * MCP tool for executing YQL queries against Vespa.
     * Supports query parameters and returns search results with metadata.
     */
    private McpStatelessServerFeatures.SyncToolSpecification executeQueryTool() {
        String inputSchema = """
            {
                "type": "object",
                "properties": {
                    "yql": { "type": "string", "description": "YQL query string", "minLength": 1 },
                    "queryProfileName": { "type": "string", "description": "Query profile to use" },
                    "parameters": { "type": "object", "description": "Optional query parameters", "additionalProperties": true }
                },
                "required": ["yql"],
                "additionalProperties": false
            }
        """;

        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name("executeQuery")
            .description("""
                Execute YQL queries against the Vespa application.
                Inspect schemas with getSchemas first, then use queryExamples resource for syntax guidance.""")
            .inputSchema(inputSchema)
            .build();

        return McpStatelessServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((context, request) -> {
                try {
                    Map<String, Object> arguments = request.arguments();
                    if (arguments == null) {
                        return new McpSchema.CallToolResult("{\"error\": \"No arguments provided\"}", true);
                    }

                    String yql = (String) arguments.get("yql");
                    if (yql == null || yql.isBlank()) {
                        return new McpSchema.CallToolResult("{\"error\": \"yql is required\"}", true);
                    }
                    if (searchChain == null) {
                        return new McpSchema.CallToolResult("{\"error\": \"No search chain available\"}", true);
                    }

                    String queryProfileName = (String) arguments.get("queryProfileName");
                    Map<String, String> params = new LinkedHashMap<>();
                    Object paramsObj = arguments.get("parameters");
                    if (paramsObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> inputParams = (Map<String, Object>) paramsObj;
                        inputParams.forEach((k, v) -> { if (k != null && v != null) params.put(k, v.toString()); });
                    }

                    Query query = new Query.Builder()
                            .setRequestMap(params)
                            .setQueryProfile(queryProfileRegistry.findQueryProfile(queryProfileName))
                            .setSchemaInfo(schemaInfo)
                            .build();
                    query.getModel().setQueryString(yql);
                    query.getModel().setType(Query.Type.YQL);

                    logger.info("Executing query: " + yql);
                    Execution execution = executionFactory.newExecution(searchChain);
                    Result result = execution.search(query);
                    execution.fill(result);
                    logger.info("Query returned " + result.getTotalHitCount() + " hits");

                    return new McpSchema.CallToolResult(toJson(resultToMap(result)), false);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Query execution failed", e);
                    return new McpSchema.CallToolResult("{\"error\": \"" + e.getMessage() + "\"}", true);
                }
            })
            .build();
    }

    /** MCP tool for reading query examples from a resource file. */
    private McpStatelessServerFeatures.SyncToolSpecification readQueryExamplesTool() {
        String inputSchema = """
            { "type": "object", "properties": {}, "required": [], "additionalProperties": false }
        """;

        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name("readQueryExamples")
            .description("Retrieves example YQL queries demonstrating how to use executeQuery.")
            .inputSchema(inputSchema)
            .build();

        return McpStatelessServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((context, request) -> {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("queryExamples.txt")) {
                    if (is == null) {
                        return new McpSchema.CallToolResult("{\"error\": \"Query examples not found\"}", true);
                    }
                    return new McpSchema.CallToolResult(toJson(new String(is.readAllBytes(), StandardCharsets.UTF_8)), false);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to read query examples", e);
                    return new McpSchema.CallToolResult("{\"error\": \"" + e.getMessage() + "\"}", true);
                }
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
        McpSchema.Resource defResource = McpSchema.Resource.builder()
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

                    List<McpSchema.PromptMessage> messages = List.of(
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
