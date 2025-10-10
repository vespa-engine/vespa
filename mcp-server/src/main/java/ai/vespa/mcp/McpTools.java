package ai.vespa.mcp;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.schema.*;
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
 * McpTools provides MCP-compatible access to Vespa search functionality.
 * This class exposes Vespa's search, schema inspection, and documentation features.
 *
 * @author Erling Fjelstad
 * @author Edvard Dingsør
 */
class McpTools {

    // Static utilities for HTTP requests and JSON parsing
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Logging
    private static final Logger logger = Logger.getLogger(McpTools.class.getName());

    // Core Vespa components injected at application startup
    private final ExecutionFactory executionFactory;
    private final Chain<Searcher> searchChain;
    private final SchemaInfo schemaInfo;

    private final CompiledQueryProfileRegistry queryProfileRegistry;
    private static final String VESPA_DOCS_BASE_URL = "https://docs.vespa.ai";
    private static final String VESPA_SEARCH_API_BASE_URL = "https://api.search.vespa.ai/search/";


    public McpTools(ExecutionFactory executionFactory, CompiledQueryProfileRegistry queryProfileRegistry) {
        this.executionFactory = executionFactory;
        this.queryProfileRegistry = queryProfileRegistry;

        // Get the default "native" search chain for query execution
        this.searchChain = executionFactory.searchChainRegistry().getChain("native");

        // SchemaInfo provides metadata about configured schemas, fields, and rank profiles
        this.schemaInfo = executionFactory.schemaInfo();
    }

    /**
     * Type-safe casting helper for Map objects from JSON parsing.
     *
     * @param obj The object to cast.
     * @return The cast object if it is a Map, or null if not.
     */
    @SuppressWarnings("unchecked")
    private static <T> T safeMapCast(Object obj) {return obj instanceof Map ? (T) obj : null;}
    /**
     * Type-safe casting helper for List objects from JSON parsing.
     *
     * @param obj The object to cast.
     * @return The cast object if it is a List, or null if not.
     */
    @SuppressWarnings("unchecked")
    private static <T> T safeListCast(Object obj) {return obj instanceof List ? (T) obj : null;}

    private Map<String, Object> error(String message, Exception e) {
        logger.log(Level.SEVERE, message, e);
        return Map.of("error", message + ": " + e.getMessage());
    }

    /**
     * Searches Vespa's public documentation for relevant content based on user input.
     *
     * @param userinput The search query provided by the user.
     * @return A map containing the search results and sources, or an error message if the search fails.
     */
    public Map<String, Object> getDocumentation(String userinput) {
        // Define query parameters: YQL with userInput function for semantic search
        Map<String, String> params = Map.of(
                "yql", "select * from doc where userInput(@userinput)",
                "userinput", userinput
        );

        // Build the full query URL with proper encoding to handle special characters
        StringBuilder urlBuilder = new StringBuilder(VESPA_SEARCH_API_BASE_URL);
        urlBuilder.append("?");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            urlBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            urlBuilder.append("=");
            urlBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            urlBuilder.append("&");
        }
        urlBuilder.deleteCharAt((urlBuilder.length() - 1)); // Remove the trailing '&'

        String fullUrl = urlBuilder.toString();

        try {
            // Prepare and send the HTTP GET request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            var response = HTTP_CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            // Parse JSON response directly to Map
            TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
            Map<String, Object> jsonResponse = OBJECT_MAPPER.readValue(response.body(), typeRef);

            // Extract search results using safe casting
            Map<String, Object> root = safeMapCast(jsonResponse.get("root"));
            List<Map<String, Object>> children = (root != null) ? safeListCast(root.get("children")) : null;

            // Build sources map with document titles and full URLs
            Map<String, String> sources = new LinkedHashMap<>();

            if (children != null) {
                for (Map<String, Object> child : children) {
                    Map<String, Object> fields = safeMapCast(child.get("fields"));
                    if (fields != null) {
                        String title = (String) fields.get("title");
                        String path = (String) fields.get("path");

                        if (title != null && path != null) {
                            // Convert relative path to full documentation URL
                            String url = VESPA_DOCS_BASE_URL + path;
                            sources.put(title, url);
                        }
                    }
                }
            }

            return Map.of(
                    "response", jsonResponse,
                    "sources", sources
            );

        } catch (Exception e) {
            return error("Error fetching documentation", e);
        }
    }

    /**
     * Retrieves comprehensive schema information from the Vespa application.
     * See {@link #getSchemas(List)}.
     * @return A map of all configured schemas, where each key is the schema name and the value is its metadata.
     */
    public Map<String, Object> getSchemas() {
        List<String> allSchemaNames = new LinkedList<>(schemaInfo.schemas().keySet());
        return getSchemas(allSchemaNames);
    }

    /**
     * Retrieves schema information for a specific list of schema names.
     *
     * @param schemaNames A list of schema names to retrieve.
     * @return A map where each key is the requested schema name and the value is its metadata.
     */
    public Map<String, Object> getSchemas(List<String> schemaNames) {
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
            fieldDetails.put((f instanceof  Field) ? "aliases" : "fieldNames", (f instanceof Field) ? ((Field) f).aliases() : ((FieldSet) f).fieldNames());
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
    public Map<String, Object> executeQuery(String yql, String queryProfileName, Map<String, String> params) {
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
    String readQueryExamples() {
        try (InputStream inputStream = JdiscMcpServer.class.getClassLoader().getResourceAsStream("queryExamples.txt")){
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
}