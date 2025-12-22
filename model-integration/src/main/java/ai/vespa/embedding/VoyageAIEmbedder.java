// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * VoyageAI embedder that uses the VoyageAI Embeddings API to generate
 * high-quality semantic embeddings for text.
 *
 * <p>Features:
 * <ul>
 *   <li>Supports all VoyageAI models (voyage-3, voyage-code-3, etc.)</li>
 *   <li>LRU caching to reduce API calls</li>
 *   <li>Auto-detection of input type (query vs document)</li>
 *   <li>Exponential backoff retry on rate limits</li>
 * </ul>
 *
 * <p><b>Future Enhancement:</b> Request batching - Currently each embed() call results
 * in a separate API request. A future enhancement will support batching multiple texts
 * in a single API request for improved efficiency and reduced latency when processing
 * multiple documents.
 *
 * <p>Configuration example in services.xml:
 * <pre>{@code
 * <component id="voyage" type="voyage-ai-embedder">
 *   <model>voyage-3</model>
 *   <api-key-secret-ref>voyage_api_key</api-key-secret-ref>
 * </component>
 * }</pre>
 *
 * @see <a href="https://docs.voyageai.com/">VoyageAI Documentation</a>
 * @author VoyageAI team
 */
@Beta
public class VoyageAIEmbedder extends AbstractComponent implements Embedder {

    private static final Logger log = Logger.getLogger(VoyageAIEmbedder.class.getName());
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // VoyageAI API endpoints - auto-selected based on model name
    private static final String EMBEDDINGS_ENDPOINT = "https://api.voyageai.com/v1/embeddings";
    private static final String MULTIMODAL_EMBEDDINGS_ENDPOINT = "https://api.voyageai.com/v1/multimodalembeddings";
    private static final String CONTEXTUALIZED_EMBEDDINGS_ENDPOINT = "https://api.voyageai.com/v1/contextualizedembeddings";

    // Configuration
    private final VoyageAiEmbedderConfig config;
    private final Embedder.Runtime runtime;
    private final Secret apiKey;
    private final OkHttpClient httpClient;
    private final String resolvedEndpoint;

    @Inject
    public VoyageAIEmbedder(VoyageAiEmbedderConfig config, Embedder.Runtime runtime, Secrets secretStore) {
        this.config = config;
        this.runtime = runtime;
        this.apiKey = getApiKey(config, secretStore);
        this.httpClient = createHttpClient(config);
        this.resolvedEndpoint = resolveEndpoint(config);

        log.info("VoyageAI embedder initialized with model: " + config.model() + ", endpoint: " + resolvedEndpoint);
    }

    /**
     * Resolve the API endpoint based on model name.
     * Different model types use different endpoints:
     * - voyage-multimodal-* models use /v1/multimodalembeddings
     * - voyage-context-* models use /v1/contextualizedembeddings
     * - All other models use /v1/embeddings
     */
    private String resolveEndpoint(VoyageAiEmbedderConfig config) {
        // If user explicitly configured an endpoint, use it
        String configuredEndpoint = config.endpoint();
        if (configuredEndpoint != null && !configuredEndpoint.equals(EMBEDDINGS_ENDPOINT)) {
            return configuredEndpoint;
        }

        // Auto-select endpoint based on model name
        String model = config.model();
        if (model != null) {
            if (model.contains("multimodal")) {
                return MULTIMODAL_EMBEDDINGS_ENDPOINT;
            }
            if (model.contains("context")) {
                return CONTEXTUALIZED_EMBEDDINGS_ENDPOINT;
            }
        }

        return EMBEDDINGS_ENDPOINT;
    }

    /**
     * Retrieve API key from Vespa's secret store.
     */
    private Secret getApiKey(VoyageAiEmbedderConfig config, Secrets secretStore) {
        String secretName = config.apiKeySecretRef();

        if (secretName == null || secretName.isEmpty()) {
            throw new IllegalArgumentException(
                "api-key-secret-ref must be configured for VoyageAI embedder. " +
                "Please set it in services.xml and ensure the secret is in the secret store."
            );
        }

        try {
            Secret secret = secretStore.get(secretName);
            if (secret == null) {
                throw new IllegalArgumentException(
                    "Secret not found in secret store: " + secretName + ". " +
                    "Please add it using: vespa secret add " + secretName + " --value YOUR_API_KEY"
                );
            }
            return secret;
        } catch (UnsupportedOperationException e) {
            throw new IllegalArgumentException(
                "Secret store is not configured. Cannot retrieve API key for VoyageAI embedder.", e
            );
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Failed to retrieve API key from secret store. Secret name: " + secretName, e
            );
        }
    }

    /**
     * Create HTTP client with connection pooling and timeouts.
     */
    private OkHttpClient createHttpClient(VoyageAiEmbedderConfig config) {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(config.timeout()))
                .readTimeout(Duration.ofMillis(config.timeout()))
                .writeTimeout(Duration.ofMillis(config.timeout()))
                .callTimeout(Duration.ofMillis(config.timeout()))
                .connectionPool(new ConnectionPool(config.maxIdleConnections(), 5, TimeUnit.MINUTES))
                .build();
    }

    @Override
    public List<Integer> embed(String text, Context context) {
        throw new UnsupportedOperationException(
            "VoyageAI embedder only supports embed() with TensorType. " +
            "Use embed(String text, Context context, TensorType targetType) instead."
        );
    }

    @Override
    public Tensor embed(String text, Context context, TensorType targetType) {
        validateTensorType(targetType);

        long startTime = System.nanoTime();

        try {
            // Check cache first using Context's cache mechanism
            String inputType = detectInputType(context);
            CacheKey cacheKey = new CacheKey(context.getEmbedderId(), text, inputType);

            Tensor result = context.computeCachedValueIfAbsent(cacheKey, () -> {
                try {
                    return callVoyageAI(text, inputType, targetType);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException("Failed to call VoyageAI API: " + e.getMessage(), e);
                }
            });

            // Record metrics
            runtime.sampleSequenceLength(text.length(), context);
            runtime.sampleEmbeddingLatency((System.nanoTime() - startTime) / 1_000_000_000.0, context);

            return result;

        } catch (RuntimeException e) {
            log.log(Level.WARNING, "VoyageAI embedding failed for model: " + config.model(), e);
            throw e;
        }
    }

    /**
     * Validate that the target tensor type is appropriate for embeddings.
     */
    private void validateTensorType(TensorType targetType) {
        if (targetType.dimensions().size() != 1) {
            throw new IllegalArgumentException(
                "Error in embedding to type '" + targetType + "': should only have one indexed dimension."
            );
        }
        if (!targetType.dimensions().get(0).isIndexed()) {
            throw new IllegalArgumentException(
                "Error in embedding to type '" + targetType + "': dimension should be indexed."
            );
        }
    }

    /**
     * Auto-detect input type (query vs document) from context.
     */
    private String detectInputType(Context context) {
        if (!config.autoDetectInputType()) {
            return config.defaultInputType().toString().toLowerCase();
        }

        // Use context destination to determine type
        String destination = context.getDestination();
        if (destination != null && destination.toLowerCase().contains("query")) {
            return "query";
        }
        return "document";
    }

    /**
     * Check if this is a multimodal model.
     */
    private boolean isMultimodalModel() {
        return config.model() != null && config.model().contains("multimodal");
    }

    /**
     * Check if this is a contextual model.
     */
    private boolean isContextualModel() {
        return config.model() != null && config.model().contains("context");
    }

    /**
     * Call VoyageAI API to get embeddings.
     */
    private Tensor callVoyageAI(String text, String inputType, TensorType targetType)
            throws IOException, InterruptedException {

        // Output dimension: null means use model default (when config is 0)
        Integer outputDimension = config.outputDimension() > 0 ? config.outputDimension() : null;

        String jsonRequest;
        if (isMultimodalModel()) {
            // Multimodal API uses different request format
            MultimodalRequest request = new MultimodalRequest(
                    text,
                    config.model(),
                    inputType,
                    config.truncate(),
                    outputDimension
            );
            jsonRequest = objectMapper.writeValueAsString(request);
        } else if (isContextualModel()) {
            // Contextual API uses array of document chunks format
            // For single text, we treat it as a single-chunk document
            ContextualRequest request = new ContextualRequest(
                    text,
                    config.model(),
                    inputType,
                    outputDimension
            );
            jsonRequest = objectMapper.writeValueAsString(request);

            log.fine(() -> "VoyageAI request: " + jsonRequest);

            // Contextual API has different response format: data[document].data[chunk].embedding
            ContextualResponse response = callContextualAPIWithRetry(jsonRequest);

            if (response.data == null || response.data.isEmpty() ||
                response.data.get(0).data == null || response.data.get(0).data.isEmpty()) {
                throw new IOException("VoyageAI contextual API returned empty response");
            }

            // Get embedding from first chunk of first document
            float[] embedding = response.data.get(0).data.get(0).embedding;
            return createTensor(embedding, targetType);
        } else {
            // Standard embeddings API
            VoyageAIRequest request = new VoyageAIRequest(
                    List.of(text),
                    config.model(),
                    inputType,
                    config.truncate(),
                    outputDimension
            );
            jsonRequest = objectMapper.writeValueAsString(request);
        }

        log.fine(() -> "VoyageAI request: " + jsonRequest);

        VoyageAIResponse response = callAPIWithRetry(jsonRequest);

        if (response.data == null || response.data.isEmpty()) {
            throw new IOException("VoyageAI API returned empty response");
        }

        float[] embedding = response.data.get(0).embedding;
        return createTensor(embedding, targetType);
    }

    /**
     * Call VoyageAI API with retry on transient failures.
     *
     * <p>Retry strategy:
     * - Retries on rate limits (429) and server errors (5xx)
     * - Uses fixed 1-second delay between retry attempts
     * - Bounded by global timeout: will not retry if it would exceed the configured timeout
     * - maxRetries provides an additional safety limit to prevent excessive retry attempts
     */
    private VoyageAIResponse callAPIWithRetry(String jsonRequest)
            throws IOException, InterruptedException {

        long startTime = System.currentTimeMillis();
        long timeoutMs = config.timeout();
        int retries = 0;
        long retryDelay = 1000; // Fixed 1 second delay

        while (true) {
            try {
                RequestBody body = RequestBody.create(jsonRequest, JSON);
                Request httpRequest = new Request.Builder()
                        .url(resolvedEndpoint)
                        .header("Authorization", "Bearer " + apiKey.current())
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        log.fine(() -> "VoyageAI response: " + responseBody);
                        return objectMapper.readValue(responseBody, VoyageAIResponse.class);
                    } else if (response.code() == 429 || response.code() >= 500) {
                        // Calculate time remaining
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        long timeRemaining = timeoutMs - elapsedTime;

                        // Check if we have time for another retry
                        if (timeRemaining <= retryDelay) {
                            String errorType = response.code() == 429 ? "rate limit" : "server error";
                            throw new IOException("VoyageAI API " + errorType + " (" + response.code() +
                                    "). Cannot retry: would exceed timeout of " + timeoutMs + "ms. Response: " + responseBody);
                        }

                        // Safety limit check
                        if (retries >= config.maxRetries()) {
                            String errorType = response.code() == 429 ? "rate limited" : "server error";
                            throw new IOException("VoyageAI API " + errorType + " (" + response.code() +
                                    "). Max retries (" + config.maxRetries() + ") exceeded. Response: " + responseBody);
                        }

                        // Retry with fixed delay
                        retries++;
                        String errorMsg = response.code() == 429 ? "rate limited" : "server error (" + response.code() + ")";
                        log.warning("VoyageAI API " + errorMsg + ". Retry " + retries +
                                " after " + retryDelay + "ms (timeout remaining: " + timeRemaining + "ms)");

                        Thread.sleep(retryDelay);

                    } else if (response.code() == 401) {
                        throw new IOException("VoyageAI API authentication failed. Please check your API key. Response: " + responseBody);

                    } else {
                        throw new IOException("VoyageAI API request failed with status " + response.code() + ": " + responseBody);
                    }
                }
            } catch (JsonProcessingException e) {
                throw new IOException("Failed to parse VoyageAI API response", e);
            }
        }
    }

    /**
     * Call VoyageAI Contextual API with retry on transient failures.
     * Similar to callAPIWithRetry but parses ContextualResponse.
     */
    private ContextualResponse callContextualAPIWithRetry(String jsonRequest)
            throws IOException, InterruptedException {

        long startTime = System.currentTimeMillis();
        long timeoutMs = config.timeout();
        int retries = 0;
        long retryDelay = 1000; // Fixed 1 second delay

        while (true) {
            try {
                RequestBody body = RequestBody.create(jsonRequest, JSON);
                Request httpRequest = new Request.Builder()
                        .url(resolvedEndpoint)
                        .header("Authorization", "Bearer " + apiKey.current())
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        log.fine(() -> "VoyageAI contextual response: " + responseBody);
                        return objectMapper.readValue(responseBody, ContextualResponse.class);
                    } else if (response.code() == 429 || response.code() >= 500) {
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        long timeRemaining = timeoutMs - elapsedTime;

                        if (timeRemaining <= retryDelay) {
                            String errorType = response.code() == 429 ? "rate limit" : "server error";
                            throw new IOException("VoyageAI API " + errorType + " (" + response.code() +
                                    "). Cannot retry: would exceed timeout of " + timeoutMs + "ms. Response: " + responseBody);
                        }

                        if (retries >= config.maxRetries()) {
                            String errorType = response.code() == 429 ? "rate limited" : "server error";
                            throw new IOException("VoyageAI API " + errorType + " (" + response.code() +
                                    "). Max retries (" + config.maxRetries() + ") exceeded. Response: " + responseBody);
                        }

                        retries++;
                        String errorMsg = response.code() == 429 ? "rate limited" : "server error (" + response.code() + ")";
                        log.warning("VoyageAI API " + errorMsg + ". Retry " + retries +
                                " after " + retryDelay + "ms (timeout remaining: " + timeRemaining + "ms)");

                        Thread.sleep(retryDelay);

                    } else if (response.code() == 401) {
                        throw new IOException("VoyageAI API authentication failed. Please check your API key. Response: " + responseBody);

                    } else {
                        throw new IOException("VoyageAI API request failed with status " + response.code() + ": " + responseBody);
                    }
                }
            } catch (JsonProcessingException e) {
                throw new IOException("Failed to parse VoyageAI API response", e);
            }
        }
    }

    /**
     * Create Vespa tensor from embedding array.
     */
    private Tensor createTensor(float[] embedding, TensorType targetType) {
        long expectedDim = targetType.dimensions().get(0).size().orElse(-1L);
        if (expectedDim != -1 && embedding.length != expectedDim) {
            throw new IllegalArgumentException(
                "VoyageAI returned " + embedding.length + " dimensions but target type expects " + expectedDim +
                ". Please ensure the model '" + config.model() + "' outputs the correct dimensions."
            );
        }

        TensorType.Builder typeBuilder = new TensorType.Builder(TensorType.Value.FLOAT);
        typeBuilder.indexed(targetType.dimensions().get(0).name(), embedding.length);
        TensorType type = typeBuilder.build();

        IndexedTensor.Builder builder = IndexedTensor.Builder.of(type);
        for (int i = 0; i < embedding.length; i++) {
            builder.cell(embedding[i], i);
        }

        Tensor result = builder.build();

        // Apply normalization if configured
        if (config.normalize()) {
            result = EmbeddingNormalizer.normalize(result, type);
        }

        return result;
    }

    @Override
    public void deconstruct() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        super.deconstruct();
    }

    // ===== Request/Response DTOs =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class VoyageAIRequest {
        @JsonProperty("input")
        public List<String> input;

        @JsonProperty("model")
        public String model;

        @JsonProperty("input_type")
        public String inputType;

        @JsonProperty("truncation")
        public boolean truncation;

        @JsonProperty("output_dimension")
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        public Integer outputDimension;

        public VoyageAIRequest(List<String> input, String model, String inputType, boolean truncation, Integer outputDimension) {
            this.input = input;
            this.model = model;
            this.inputType = inputType;
            this.truncation = truncation;
            this.outputDimension = outputDimension;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class VoyageAIResponse {
        @JsonProperty("data")
        public List<EmbeddingData> data;

        @JsonProperty("model")
        public String model;

        @JsonProperty("usage")
        public Usage usage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbeddingData {
        @JsonProperty("embedding")
        public float[] embedding;

        @JsonProperty("index")
        public int index;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Usage {
        @JsonProperty("total_tokens")
        public int totalTokens;
    }

    // ===== Contextual Request/Response DTOs =====

    /**
     * Request format for contextual embeddings API (voyage-context-*).
     * Uses "inputs" as array of document chunks: [["chunk1", "chunk2"], ["chunk1", "chunk2"]]
     * For single text embedding, we treat it as a single-chunk document: [["text"]]
     * Note: Contextual API does not support truncation parameter.
     * Supports output_dimension: 2048, 1024 (default), 512, 256
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ContextualRequest {
        @JsonProperty("inputs")
        public List<List<String>> inputs;

        @JsonProperty("model")
        public String model;

        @JsonProperty("input_type")
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        public String inputType;

        @JsonProperty("output_dimension")
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        public Integer outputDimension;

        public ContextualRequest(String text, String model, String inputType, Integer outputDimension) {
            // Single text is treated as a single-chunk document
            this.inputs = List.of(List.of(text));
            this.model = model;
            this.inputType = inputType;
            this.outputDimension = outputDimension;
        }
    }

    /**
     * Response format for contextual embeddings API.
     * The actual response structure is nested: data[document].data[chunk].embedding
     * {"object":"list","data":[{"object":"list","data":[{"object":"embedding","embedding":[...]}]}]}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ContextualResponse {
        @JsonProperty("data")
        public List<ContextualDocumentResult> data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ContextualDocumentResult {
        @JsonProperty("data")
        public List<EmbeddingData> data;  // Reuse existing EmbeddingData class
    }

    // ===== Multimodal Request DTOs =====

    /**
     * Request format for multimodal embeddings API.
     * Uses "inputs" with content array instead of "input" with string list.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MultimodalRequest {
        @JsonProperty("inputs")
        public List<MultimodalInput> inputs;

        @JsonProperty("model")
        public String model;

        @JsonProperty("input_type")
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        public String inputType;

        @JsonProperty("truncation")
        public boolean truncation;

        @JsonProperty("output_dimension")
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        public Integer outputDimension;

        public MultimodalRequest(String text, String model, String inputType, boolean truncation, Integer outputDimension) {
            this.inputs = List.of(new MultimodalInput(text));
            this.model = model;
            this.inputType = inputType;
            this.truncation = truncation;
            this.outputDimension = outputDimension;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MultimodalInput {
        @JsonProperty("content")
        public List<ContentItem> content;

        public MultimodalInput(String text) {
            this.content = List.of(new ContentItem(text));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ContentItem {
        @JsonProperty("type")
        public String type;

        @JsonProperty("text")
        public String text;

        public ContentItem(String text) {
            this.type = "text";
            this.text = text;
        }
    }

    // ===== Cache Key =====

    private record CacheKey(String embedderId, String text, String inputType) {}
}
