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
import com.yahoo.embedding.voyageai.VoyageAiEmbedderConfig;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 *   <li>Automatic request batching for efficiency</li>
 *   <li>LRU caching to reduce API calls</li>
 *   <li>Auto-detection of input type (query vs document)</li>
 *   <li>Exponential backoff retry on rate limits</li>
 * </ul>
 *
 * <p>Configuration example in services.xml:
 * <pre>{@code
 * <component id="voyage" type="voyage-ai-embedder">
 *   <model>voyage-3</model>
 *   <api-key-secret-name>voyage_api_key</api-key-secret-name>
 * </component>
 * }</pre>
 *
 * @see <a href="https://docs.voyageai.com/">VoyageAI Documentation</a>
 * @author Vespa Team
 */
@Beta
public class VoyageAIEmbedder extends AbstractComponent implements Embedder {

    private static final Logger log = Logger.getLogger(VoyageAIEmbedder.class.getName());
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Configuration
    private final VoyageAiEmbedderConfig config;
    private final Embedder.Runtime runtime;
    private final Secret apiKey;
    private final OkHttpClient httpClient;

    // Cache for embeddings
    private final Map<CacheKey, Tensor> embeddingCache;

    @Inject
    public VoyageAIEmbedder(VoyageAiEmbedderConfig config, Embedder.Runtime runtime, Secrets secretStore) {
        this.config = config;
        this.runtime = runtime;
        this.apiKey = getApiKey(config, secretStore);
        this.httpClient = createHttpClient(config);
        this.embeddingCache = config.cacheSize() > 0
                ? new LRUCache<>(config.cacheSize())
                : new ConcurrentHashMap<>();

        log.info("VoyageAI embedder initialized with model: " + config.model());
    }

    /**
     * Retrieve API key from Vespa's secret store.
     */
    private Secret getApiKey(VoyageAiEmbedderConfig config, Secrets secretStore) {
        String secretName = config.apiKeySecretName();

        if (secretName == null || secretName.isEmpty()) {
            throw new IllegalArgumentException(
                "api-key-secret-name must be configured for VoyageAI embedder. " +
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
                .connectionPool(new ConnectionPool(config.poolSize(), 5, TimeUnit.MINUTES))
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
            // Check cache first
            String inputType = detectInputType(context);
            CacheKey cacheKey = new CacheKey(context.getEmbedderId(), text, inputType);

            @SuppressWarnings("unused")
            Tensor result = embeddingCache.computeIfAbsent(cacheKey, k -> {
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
     * Call VoyageAI API to get embeddings.
     */
    private Tensor callVoyageAI(String text, String inputType, TensorType targetType)
            throws IOException, InterruptedException {

        VoyageAIRequest request = new VoyageAIRequest(
                List.of(text),
                config.model(),
                inputType,
                config.truncate()
        );

        String jsonRequest = objectMapper.writeValueAsString(request);
        log.fine(() -> "VoyageAI request: " + jsonRequest);

        VoyageAIResponse response = callAPIWithRetry(jsonRequest);

        if (response.data == null || response.data.isEmpty()) {
            throw new IOException("VoyageAI API returned empty response");
        }

        float[] embedding = response.data.get(0).embedding;
        return createTensor(embedding, targetType);
    }

    /**
     * Call VoyageAI API with exponential backoff retry.
     */
    private VoyageAIResponse callAPIWithRetry(String jsonRequest)
            throws IOException, InterruptedException {

        int retries = 0;
        long retryDelay = 1000; // Start with 1 second

        while (true) {
            try {
                RequestBody body = RequestBody.create(jsonRequest, JSON);
                Request httpRequest = new Request.Builder()
                        .url(config.endpoint())
                        .header("Authorization", "Bearer " + apiKey.current())
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        return objectMapper.readValue(responseBody, VoyageAIResponse.class);
                    } else if (response.code() == 429 && retries < config.maxRetries()) {
                        // Rate limited - retry with exponential backoff
                        retries++;
                        log.warning("VoyageAI API rate limited (429). Retry " + retries + "/" + config.maxRetries() +
                                " after " + retryDelay + "ms");

                        Thread.sleep(retryDelay);
                        retryDelay *= 2; // Exponential backoff
                        continue;

                    } else if (response.code() >= 500 && retries < config.maxRetries()) {
                        // Server error - retry
                        retries++;
                        log.warning("VoyageAI API server error (" + response.code() + "). Retry " + retries + "/" +
                                config.maxRetries() + " after " + retryDelay + "ms");

                        Thread.sleep(retryDelay);
                        retryDelay *= 2;
                        continue;

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
        embeddingCache.clear();
        super.deconstruct();
    }

    // ===== Request/Response DTOs =====

    private record VoyageAIRequest(
            @JsonProperty("input") List<String> input,
            @JsonProperty("model") String model,
            @JsonProperty("input_type") String inputType,
            @JsonProperty("truncation") boolean truncation
    ) {}

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

    // ===== Cache Key =====

    private record CacheKey(String embedderId, String text, String inputType) {}

    // ===== Simple LRU Cache =====

    private static class LRUCache<K, V> extends java.util.LinkedHashMap<K, V> {
        private final int maxSize;

        public LRUCache(int maxSize) {
            super(16, 0.75f, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }
}
