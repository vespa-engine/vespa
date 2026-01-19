// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.OverloadException;
import com.yahoo.language.process.TimeoutException;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * VoyageAI embedder that uses the VoyageAI Embeddings API.
 *
 * @see <a href="https://docs.voyageai.com/">VoyageAI Documentation</a>
 * @author VoyageAI team
 * @author bjorncs
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

        log.fine(() -> "VoyageAI embedder initialized with model: %s, endpoint: %s".formatted(config.model(), resolvedEndpoint));
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
            if (model.startsWith("voyage-multimodal-")) {
                return MULTIMODAL_EMBEDDINGS_ENDPOINT;
            }
            if (model.startsWith("voyage-context-")) {
                return CONTEXTUALIZED_EMBEDDINGS_ENDPOINT;
            }
        }

        return EMBEDDINGS_ENDPOINT;
    }

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

    private OkHttpClient createHttpClient(VoyageAiEmbedderConfig config) {
        return new OkHttpClient.Builder()
                .addInterceptor(new RetryInterceptor(config.maxRetries()))
                .retryOnConnectionFailure(true)
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofMillis(config.timeout()))
                .writeTimeout(Duration.ofMillis(config.timeout()))
                .connectionPool(new ConnectionPool(10, 1, TimeUnit.MINUTES))
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

        String inputType = detectInputType(context);
        CacheKey cacheKey = new CacheKey(context.getEmbedderId(), text, inputType);
        Tensor result = context.computeCachedValueIfAbsent(cacheKey, () ->
                callVoyageAI(text, inputType, targetType, context));

        runtime.sampleSequenceLength(text.length(), context);
        runtime.sampleEmbeddingLatency((System.nanoTime() - startTime) / 1_000_000_000.0, context);

        return result;
    }

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

    private String detectInputType(Context context) {
        String override = config.inputTypeOverride().toString().toLowerCase();
        if (!override.equals("auto")) {
            return override;  // "query" or "document"
        }

        // Auto-detect from context destination
        String destination = context.getDestination();
        if (destination != null && destination.toLowerCase().contains("query")) {
            return "query";
        }
        return "document";
    }

    private boolean isMultimodalModel() {
        return config.model() != null && config.model().startsWith("voyage-multimodal-");
    }

    private boolean isContextualModel() {
        return config.model() != null && config.model().startsWith("voyage-context-");
    }

    /**
     * Validate and get the output dimension from the target tensor type.
     * For models that support variable dimensions, validates that the dimension is one of: 256, 512, 1024, 2048.
     * Returns null if the model doesn't support variable dimensions (use model's default).
     */
    private Optional<Integer> getOutputDimensionFromTensorType(TensorType targetType) {
        long dim = targetType.dimensions().get(0).size().orElse(-1L);
        if (dim == -1) {
            return Optional.empty();  // Unbound dimension, use model default
        }
        if (!(isMultimodalModel() || isContextualModel())) {
            return Optional.empty();  // Model doesn't support variable dimensions, API will return default
        }

        int dimension = (int) dim;
        if (dimension != 256 && dimension != 512 && dimension != 1024 && dimension != 2048) {
            throw new IllegalArgumentException(
                "Invalid output dimension " + dimension + " for model '" + config.model() + "'. " +
                "Supported dimensions are: 256, 512, 1024, 2048. " +
                "Please adjust your schema's tensor type accordingly."
            );
        }
        return Optional.of(dimension);
    }

    private Tensor callVoyageAI(String text, String inputType, TensorType targetType, Context context) {

        Integer outputDimension = getOutputDimensionFromTensorType(targetType).orElse(null);

        String jsonRequest;
        if (isMultimodalModel()) {
            MultimodalRequest request = new MultimodalRequest(
                    text,
                    config.model(),
                    inputType,
                    config.truncate(),
                    outputDimension
            );
            try {
                jsonRequest = objectMapper.writeValueAsString(request);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize multimodal request", e);
            }
        } else if (isContextualModel()) {
            // Contextual API uses array of document chunks format
            // For single text, we treat it as a single-chunk document
            ContextualRequest request = new ContextualRequest(
                    text,
                    config.model(),
                    inputType,
                    outputDimension
            );
            try {
                jsonRequest = objectMapper.writeValueAsString(request);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize contextual request", e);
            }

            log.fine(() -> "VoyageAI request: " + jsonRequest);

            var response = doRequest(jsonRequest, ContextualResponse.class, context);

            if (response.data == null || response.data.isEmpty() ||
                response.data.get(0).data == null || response.data.get(0).data.isEmpty()) {
                throw new RuntimeException("VoyageAI contextual API returned empty response");
            }

            // Get embedding from first chunk of first document
            float[] embedding = response.data.get(0).data.get(0).embedding;
            return createTensor(embedding, targetType);
        } else {
            VoyageAIRequest request = new VoyageAIRequest(
                    List.of(text),
                    config.model(),
                    inputType,
                    config.truncate(),
                    outputDimension
            );
            try {
                jsonRequest = objectMapper.writeValueAsString(request);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize request", e);
            }
        }

        log.fine(() -> "VoyageAI request: " + jsonRequest);

        var response = doRequest(jsonRequest, VoyageAIResponse.class, context);

        if (response.data == null || response.data.isEmpty()) {
            throw new RuntimeException("VoyageAI API returned empty response");
        }

        float[] embedding = response.data.get(0).embedding;
        return createTensor(embedding, targetType);
    }

    private <T> T doRequest(String jsonRequest, Class<T> responseType, Context context) {
        long startTime = System.currentTimeMillis();

        var httpRequest = new Request.Builder()
                .url(resolvedEndpoint)
                .header("Authorization", "Bearer " + apiKey.current())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonRequest, JSON))
                .build();

        var call = httpClient.newCall(httpRequest);
        var timeoutMs = calculateTimeoutMs(context, startTime);
        call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS);

        try (var response = call.execute()) {
            var responseBody = response.body() != null ? response.body().string() : "";
            log.fine(() -> "VoyageAI response with code " + response.code() + ": " + responseBody);
            if (response.isSuccessful()) {
                return objectMapper.readValue(responseBody, responseType);
            } else if (response.code() == 429) {
                throw new OverloadException("VoyageAI API rate limited (429)");
            } else if (response.code() == 401) {
                throw new RuntimeException("VoyageAI API authentication failed. Please check your API key: " + responseBody);
            } else {
                throw new RuntimeException("VoyageAI API request failed with status " + response.code() + ": " + responseBody);
            }
        } catch (java.io.InterruptedIOException e) {
            // Covers both OkHttp timeout (InterruptedIOException) and socket timeout (SocketTimeoutException extends InterruptedIOException)
            throw new TimeoutException(
                    "VoyageAI API call timed out after " + timeoutMs + "ms", e);
        } catch (IOException e) {
            throw new RuntimeException("VoyageAI API call failed: " + e.getMessage(), e);
        }
    }

    private long calculateTimeoutMs(Context context, long startTime) {
        long remainingMs = context.getDeadline().isPresent()
                ? context.getDeadline().get().timeRemaining().toMillis()
                : config.timeout() - (System.currentTimeMillis() - startTime);
        if (remainingMs <= 0) {
            throw new TimeoutException("Request deadline exceeded before VoyageAI API call");
        }
        return remainingMs;
    }

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

        return builder.build();
    }

    @Override
    public void deconstruct() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        super.deconstruct();
    }

    private static class RetryInterceptor implements Interceptor {
        static final long RETRY_DELAY_MS = 100;
        private final int maxRetries;

        RetryInterceptor(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            var request = chain.request();
            int lastStatusCode = 0;
            String lastResponseBody = "";

            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    var response = chain.proceed(request);

                    int code = response.code();
                    boolean shouldRetry = code == 500 || code == 502 || code == 503 || code == 504;
                    if (response.isSuccessful() || !shouldRetry) return response;

                    // Capture response details before closing
                    lastStatusCode = code;
                    if (response.body() != null) {
                        lastResponseBody = response.body().string();
                    }
                    response.close();

                    if (attempt < maxRetries) {
                        int retryNumber = attempt + 1;
                        log.fine(() -> "VoyageAI API server error (%d). Retry %d of %d after %dms"
                                .formatted(code, retryNumber + 1, maxRetries, RETRY_DELAY_MS));
                        Thread.sleep(RETRY_DELAY_MS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", e);
                }
            }

            var errorMsg = "Max retries exceeded for VoyageAI API (%d). Last response: %d - %s"
                    .formatted(maxRetries, lastStatusCode, lastResponseBody);
            throw new IOException(errorMsg);
        }
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
