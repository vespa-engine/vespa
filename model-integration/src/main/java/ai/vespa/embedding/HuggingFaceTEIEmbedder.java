// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.HuggingFaceTeiEmbedderConfig;
import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvalidInputException;
import com.yahoo.language.process.OverloadException;
import com.yahoo.language.process.TimeoutException;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.Text;
import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Embedder backed by Hugging Face Text Embeddings Inference API.
 *
 * @see <a href="https://huggingface.github.io/text-embeddings-inference/">Text Embeddings Inference</a>
 */
@Beta
public class HuggingFaceTEIEmbedder extends AbstractComponent implements Embedder {

    private static final Logger log = Logger.getLogger(HuggingFaceTEIEmbedder.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private static final String DEFAULT_ENDPOINT = "http://localhost:8080/embed";

    private final HuggingFaceTeiEmbedderConfig config;
    private final Embedder.Runtime runtime;
    private final Embedder.Batching batching;
    private final Optional<Secret> apiKey;
    private final OkHttpClient httpClient;
    private final String endpoint;

    @Inject
    public HuggingFaceTEIEmbedder(HuggingFaceTeiEmbedderConfig config, Embedder.Runtime runtime, Secrets secretStore) {
        this.config = config;
        this.runtime = runtime;
        this.batching = Embedder.Batching.of(config.batching().maxSize(), Duration.ofMillis(config.batching().maxDelayMillis()));
        this.apiKey = resolveApiKey(config.apiKeySecretRef(), secretStore);
        this.httpClient = createHttpClient(config);
        this.endpoint = normalizeEndpoint(config.endpoint());

        log.fine(() -> Text.format("HuggingFace TEI embedder initialized with endpoint: %s", endpoint));
    }

    @Override
    public Batching batchingConfig() { return batching; }

    @Override
    public List<Integer> embed(String text, Context context) {
        throw new UnsupportedOperationException(
                "HuggingFace TEI embedder only supports embed() with TensorType. " +
                "Use embed(String text, Context context, TensorType targetType) instead.");
    }

    @Override
    public Tensor embed(String text, Context context, TensorType targetType) {
        return invokeTEI(List.of(text), context, targetType).get(0);
    }

    @Override
    public List<Tensor> embed(List<String> texts, Context context, TensorType targetType) {
        return invokeTEI(texts, context, targetType);
    }

    private List<Tensor> invokeTEI(List<String> texts, Context context, TensorType targetType) {
        if (texts.isEmpty()) return List.of();

        validateTensorType(targetType);
        var inputTexts = List.copyOf(texts);
        var promptName = resolvePromptName(context);
        var requestedDimensions = resolveRequestedDimensions(targetType);
        var timeoutMs = calculateTimeoutMs(context);

        var cacheKey = new CacheKey(context.getEmbedderId(), inputTexts, requestedDimensions, promptName,
                                    config.normalize(), config.truncate(), config.truncationDirection().name());

        var startTime = System.nanoTime();
        var responseBody = context.computeCachedValueIfAbsent(cacheKey,
                () -> doRequest(createJsonRequest(inputTexts, promptName, requestedDimensions), timeoutMs, context));

        var embeddings = parseEmbeddings(responseBody);
        if (embeddings.size() != inputTexts.size()) {
            throw new RuntimeException(Text.format(
                    "HuggingFace TEI returned %d embeddings for %d inputs.", embeddings.size(), inputTexts.size()));
        }

        var tensors = embeddings.stream()
                .map(embedding -> toTensor(embedding, targetType, requestedDimensions))
                .toList();

        runtime.sampleEmbeddingLatency(Duration.ofNanos(System.nanoTime() - startTime).toMillis(), context);
        return tensors;
    }

    private void validateTensorType(TensorType targetTensorType) {
        if (targetTensorType.dimensions().size() != 1) {
            throw new IllegalArgumentException(
                    "Error in embedding to type '" + targetTensorType + "': should only have one dimension.");
        }

        var tensorDimension = targetTensorType.dimensions().get(0);
        if (!tensorDimension.isIndexed()) {
            throw new IllegalArgumentException(
                    "Error in embedding to type '" + targetTensorType + "': dimension should be indexed.");
        }

        var valueType = targetTensorType.valueType();
        if (valueType != TensorType.Value.FLOAT && valueType != TensorType.Value.BFLOAT16) {
            throw new IllegalArgumentException(
                    "HuggingFace TEI embedder only supports tensor<float> and tensor<bfloat16>, got " + targetTensorType + ".");
        }

        var configuredDimensions = config.dimensions();
        var tensorDimensions = tensorDimension.size();

        if (configuredDimensions < 0) {
            throw new IllegalArgumentException("Configured dimensions must be non-negative, got: " + configuredDimensions);
        }

        if (configuredDimensions > 0 && tensorDimensions.isPresent() && tensorDimensions.get() != configuredDimensions) {
            throw new IllegalArgumentException(Text.format(
                    "Tensor dimension %d does not match configured dimension %d.",
                    tensorDimensions.get(), configuredDimensions));
        }
    }

    private Integer resolveRequestedDimensions(TensorType targetTensorType) {
        if (config.dimensions() > 0) return config.dimensions();

        Optional<Long> tensorDimension = targetTensorType.dimensions().get(0).size();
        if (tensorDimension.isPresent()) {
            return Math.toIntExact(tensorDimension.get());
        }

        return null;
    }

    private String resolvePromptName(Context context) {
        if (context.getDestinationType() == Context.DestinationType.QUERY && !config.queryPromptName().isBlank()) {
            return config.queryPromptName();
        }
        if (context.getDestinationType() == Context.DestinationType.DOCUMENT && !config.documentPromptName().isBlank()) {
            return config.documentPromptName();
        }
        if (!config.promptName().isBlank()) {
            return config.promptName();
        }
        return null;
    }

    private String createJsonRequest(List<String> texts, String promptName, Integer requestedDimensions) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            if (texts.size() == 1) {
                root.put("inputs", texts.get(0));
            } else {
                var array = root.putArray("inputs");
                texts.forEach(array::add);
            }
            root.put("normalize", config.normalize());
            root.put("truncate", config.truncate());
            root.put("truncation_direction", toTeiTruncationDirection(config.truncationDirection()));
            if (promptName != null && !promptName.isBlank()) {
                root.put("prompt_name", promptName);
            }
            if (requestedDimensions != null) {
                root.put("dimensions", requestedDimensions);
            }

            return objectMapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize HuggingFace TEI request", e);
        }
    }

    private String doRequest(String jsonRequest, long timeoutMs, Context context) {
        var builder = new Request.Builder()
                .url(endpoint)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonRequest, JSON_MEDIA_TYPE));

        apiKey.ifPresent(secret -> builder.header("Authorization", "Bearer " + secret.current()));

        runtime.sampleRequestCount(context);

        var call = httpClient.newCall(builder.build());
        call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS);

        try (var response = call.execute()) {
            var responseBody = response.body() != null ? response.body().string() : "";
            log.fine(() -> "HuggingFace TEI response with code " + response.code() + ": " + responseBody);

            if (response.isSuccessful()) return responseBody;

            runtime.sampleRequestFailure(context, response.code());
            var errorDetail = parseErrorDetail(responseBody).orElse(responseBody);

            if (response.code() == 429) {
                throw new OverloadException("HuggingFace TEI API overloaded (429)");
            }
            if (response.code() == 400 || response.code() == 413 || response.code() == 422) {
                throw new InvalidInputException("HuggingFace TEI API bad request (" + response.code() + "): " + errorDetail);
            }

            throw new RuntimeException(
                    "HuggingFace TEI API request failed with status " + response.code() + ": " + errorDetail);
        } catch (InterruptedIOException e) {
            runtime.sampleRequestFailure(context, 0);
            throw new TimeoutException("HuggingFace TEI API call timed out after " + timeoutMs + "ms", e);
        } catch (IOException e) {
            runtime.sampleRequestFailure(context, 0);
            throw new RuntimeException("HuggingFace TEI API call failed: " + e.getMessage(), e);
        }
    }

    private List<List<Float>> parseEmbeddings(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.isArray()) {
                return parseArrayResponse(root);
            }

            if (root.isObject() && root.has("data") && root.get("data").isArray()) {
                return parseOpenAICompatibleResponse(root.get("data"));
            }

            throw new IllegalArgumentException("Unexpected HuggingFace TEI response format: " + root.getNodeType());
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse HuggingFace TEI response", e);
        }
    }

    private List<List<Float>> parseArrayResponse(JsonNode root) {
        if (root.isEmpty()) return List.of();

        if (root.get(0).isNumber()) {
            return List.of(parseEmbeddingVector(root));
        }

        var embeddings = new ArrayList<List<Float>>(root.size());
        for (var item : root) {
            embeddings.add(parseEmbeddingVector(item));
        }
        return embeddings;
    }

    private List<List<Float>> parseOpenAICompatibleResponse(JsonNode dataArray) {
        var embeddings = new ArrayList<List<Float>>(dataArray.size());
        for (var item : dataArray) {
            var embedding = item.get("embedding");
            if (embedding == null) {
                throw new IllegalArgumentException("Missing 'embedding' field in OpenAI-compatible response item");
            }
            if (embedding.isArray()) {
                embeddings.add(parseEmbeddingVector(embedding));
            } else if (embedding.isTextual()) {
                embeddings.add(decodeBase64FloatVector(embedding.textValue()));
            } else {
                throw new IllegalArgumentException("Unsupported 'embedding' value in OpenAI-compatible response: " + embedding);
            }
        }
        return embeddings;
    }

    private List<Float> parseEmbeddingVector(JsonNode vectorNode) {
        if (!vectorNode.isArray()) {
            throw new IllegalArgumentException("Expected embedding vector array, got: " + vectorNode.getNodeType());
        }

        var values = new ArrayList<Float>(vectorNode.size());
        for (var value : vectorNode) {
            if (!value.isNumber()) {
                throw new IllegalArgumentException("Expected numeric embedding value, got: " + value);
            }
            values.add(value.floatValue());
        }
        return values;
    }

    private List<Float> decodeBase64FloatVector(String base64) {
        var buffer = ByteBuffer.wrap(Base64.getDecoder().decode(base64)).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        var values = new ArrayList<Float>(buffer.remaining());
        while (buffer.hasRemaining()) {
            values.add(buffer.get());
        }
        return values;
    }

    private Tensor toTensor(List<Float> embedding, TensorType targetType, Integer requestedDimensions) {
        var dimension = targetType.dimensions().get(0);
        long actualDimensions = embedding.size();
        long expectedDimensions = requestedDimensions != null
                ? requestedDimensions
                : dimension.size().orElse(actualDimensions);

        if (actualDimensions != expectedDimensions) {
            throw new IllegalArgumentException(Text.format(
                    "Expected embedding dimension %d, got %d.", expectedDimensions, actualDimensions));
        }

        var tensorType = new TensorType.Builder(targetType.valueType())
                .indexed(dimension.name(), actualDimensions)
                .build();

        var tensorBuilder = IndexedTensor.Builder.of(tensorType);
        for (int i = 0; i < embedding.size(); i++) {
            tensorBuilder.cell(embedding.get(i), i);
        }
        return tensorBuilder.build();
    }

    private Optional<String> parseErrorDetail(String responseBody) {
        try {
            var error = objectMapper.readValue(responseBody, ErrorResponse.class);
            if (error.error != null && !error.error.isBlank()) return Optional.of(error.error);
            if (error.message != null && !error.message.isBlank()) return Optional.of(error.message);
        } catch (IOException e) {
            log.fine(() -> "Failed to parse TEI error response as JSON: " + e.getMessage());
        }
        return Optional.empty();
    }

    private long calculateTimeoutMs(Context context) {
        long remainingMs = context.getDeadline()
                .map(d -> d.timeRemaining().toMillis())
                .orElse((long) config.timeout());

        if (remainingMs <= 0) {
            throw new TimeoutException("Request deadline exceeded before HuggingFace TEI API call");
        }
        return remainingMs;
    }

    private static OkHttpClient createHttpClient(HuggingFaceTeiEmbedderConfig config) {
        return new OkHttpClient.Builder()
                .addInterceptor(new RetryInterceptor(config.maxRetries()))
                .retryOnConnectionFailure(true)
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofMillis(config.timeout()))
                .writeTimeout(Duration.ofMillis(config.timeout()))
                .connectionPool(new ConnectionPool(10, 1, TimeUnit.MINUTES))
                .build();
    }

    private static Optional<Secret> resolveApiKey(String secretRef, Secrets secretStore) {
        if (secretRef == null || secretRef.isBlank()) return Optional.empty();
        Secret secret = secretStore.get(secretRef);
        if (secret == null) {
            throw new IllegalArgumentException("No secret found for api-key-secret-ref '" + secretRef + "'.");
        }
        return Optional.of(secret);
    }

    private static String normalizeEndpoint(String endpoint) {
        String value = (endpoint == null || endpoint.isBlank()) ? DEFAULT_ENDPOINT : endpoint;
        if (value.endsWith("/embed") || value.endsWith("/v1/embeddings")) {
            return value;
        }
        if (value.endsWith("/")) {
            return value + "embed";
        }
        return value + "/embed";
    }

    private static String toTeiTruncationDirection(HuggingFaceTeiEmbedderConfig.TruncationDirection.Enum direction) {
        return switch (direction) {
            case LEFT -> "Left";
            case RIGHT -> "Right";
        };
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

                    lastStatusCode = code;
                    if (response.body() != null) {
                        lastResponseBody = response.body().string();
                    }
                    response.close();

                    if (attempt < maxRetries) {
                        int retryNumber = attempt + 1;
                        log.fine(() -> Text.format(
                                "HuggingFace TEI API server error (%d). Retry %d of %d after %dms",
                                code, retryNumber + 1, maxRetries, RETRY_DELAY_MS));
                        Thread.sleep(RETRY_DELAY_MS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", e);
                }
            }

            throw new IOException(Text.format(
                    "Max retries exceeded for HuggingFace TEI API (%d). Last response: %d - %s",
                    maxRetries, lastStatusCode, lastResponseBody));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorResponse(
            @JsonProperty("error") String error,
            @JsonProperty("error_type") String errorType,
            @JsonProperty("message") String message) {}

    private record CacheKey(String embedderId,
                            List<String> texts,
                            Integer dimensions,
                            String promptName,
                            boolean normalize,
                            boolean truncate,
                            String truncationDirection) {}
}
