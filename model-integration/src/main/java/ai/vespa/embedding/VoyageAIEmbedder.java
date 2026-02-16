// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Duration;
import java.util.List;
import java.util.Locale;
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
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // VoyageAI API endpoints - auto-selected based on model name
    private static final String EMBEDDINGS_ENDPOINT = "https://api.voyageai.com/v1/embeddings";
    private static final String MULTIMODAL_EMBEDDINGS_ENDPOINT = "https://api.voyageai.com/v1/multimodalembeddings";
    private static final String CONTEXTUALIZED_EMBEDDINGS_ENDPOINT = "https://api.voyageai.com/v1/contextualizedembeddings";

    // Configuration
    private final VoyageAiEmbedderConfig config;
    private final Embedder.Runtime runtime;
    private final Embedder.Batching batching;
    private final Secret apiKey;
    private final OkHttpClient httpClient;
    private final String resolvedEndpoint;

    @Inject
    public VoyageAIEmbedder(VoyageAiEmbedderConfig config, Embedder.Runtime runtime, Secrets secretStore) {
        this.config = config;
        this.runtime = runtime;
        this.batching = Embedder.Batching.of(config.batching().maxSize(), Duration.ofMillis(config.batching().maxDelayMillis()));
        this.apiKey = secretStore.get(config.apiKeySecretRef());
        this.httpClient = createHttpClient(config);
        this.resolvedEndpoint = resolveEndpoint(config);

        log.fine(() -> Text.format("VoyageAI embedder initialized with model: %s, endpoint: %s", config.model(), resolvedEndpoint));
    }

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
    public Batching batchingConfig() { return batching; }

    @Override
    public List<Integer> embed(String text, Context context) {
        throw new UnsupportedOperationException(
            "VoyageAI embedder only supports embed() with TensorType. " +
            "Use embed(String text, Context context, TensorType targetType) instead."
        );
    }

    @Override
    public Tensor embed(String text, Context context, TensorType targetType) {
        return invokeVoyageAI(List.of(text), context, targetType)
                .get(0);
    }

    @Override
    public List<Tensor> embed(List<String> texts, Context context, TensorType targetType) {
        return invokeVoyageAI(texts, context, targetType);
    }

    private void validateTensorType(TensorType targetTensorType) {
        if (targetTensorType.dimensions().size() != 1)
            throw new IllegalArgumentException(
                    "Error in embedding to type '" + targetTensorType + "': should only have one dimension.");
        var tensorDimension = targetTensorType.dimensions().get(0);
        if (!tensorDimension.isIndexed())
            throw new IllegalArgumentException(
                    "Error in embedding to type '" + targetTensorType + "': dimension should be indexed.");
        var valueType = targetTensorType.valueType();

        int configuredDim = config.dimensions();
        long tensorDim = tensorDimension.size().orElseThrow();

        switch (config.quantization()) {
            case AUTO:
                if (valueType == TensorType.Value.FLOAT || valueType == TensorType.Value.BFLOAT16) {
                    if (tensorDim != configuredDim)
                        throw new IllegalArgumentException(Text.format("Tensor dimension %d does not match configured dimension %d.", tensorDim, configuredDim));
                } else if (valueType == TensorType.Value.INT8) {
                    if (tensorDim != configuredDim && tensorDim != configuredDim / 8)
                        throw new IllegalArgumentException(Text.format("Tensor dimension %d does not match configured dimension. Expected %d or %d.", tensorDim, configuredDim, configuredDim / 8));
                } else {
                    throw new IllegalArgumentException(
                            "Quantization 'auto' is incompatible with tensor type " + targetTensorType + ".");
                }
                break;
            case FLOAT:
                if (valueType != TensorType.Value.FLOAT && valueType != TensorType.Value.BFLOAT16)
                    throw new IllegalArgumentException(
                            "Quantization 'float' is incompatible with tensor type " + targetTensorType + ".");
                if (tensorDim != configuredDim)
                    throw new IllegalArgumentException(Text.format("Tensor dimension %d does not match configured dimension %d.", tensorDim, configuredDim));
                break;
            case INT8:
                if (valueType != TensorType.Value.INT8)
                    throw new IllegalArgumentException(
                            "Quantization 'int8' is incompatible with tensor type " + targetTensorType + ".");
                if (tensorDim != configuredDim)
                    throw new IllegalArgumentException(Text.format("Tensor dimension %d does not match configured dimension %d.", tensorDim, configuredDim));
                break;
            case BINARY:
                if (valueType != TensorType.Value.INT8)
                    throw new IllegalArgumentException(
                            "Quantization 'binary' is incompatible with tensor type " + targetTensorType + ".");
                if (tensorDim != configuredDim / 8)
                    throw new IllegalArgumentException(Text.format("Tensor dimension %d does not match required dimension %d.", tensorDim, configuredDim / 8));
                break;
            default:
                throw new IllegalArgumentException("Unsupported quantization: " + config.quantization());
        }
    }

    private boolean isMultimodalModel() { return config.model().startsWith("voyage-multimodal-"); }
    private boolean isContextualModel() { return config.model().startsWith("voyage-context-"); }

    private String resolveOutputDataType(TensorType targetType) {
        return switch (config.quantization()) {
            case AUTO -> {
                if (targetType.valueType() == TensorType.Value.FLOAT || targetType.valueType() == TensorType.Value.BFLOAT16) {
                    yield "float";
                } else if (targetType.valueType() == TensorType.Value.INT8) {
                    long tensorDim = targetType.dimensions().get(0).size().orElseThrow();
                    yield (tensorDim == config.dimensions()) ? "int8" : "binary";
                } else {
                    throw new IllegalStateException();
                }
            }
            case FLOAT -> "float";
            case INT8 -> "int8";
            case BINARY -> "binary";
        };
    }

    private List<Tensor> invokeVoyageAI(List<String> texts, Context context, TensorType targetType) {
        long startTime = System.nanoTime();
        validateTensorType(targetType);
        var inputType = context.getDestinationType() == Context.DestinationType.QUERY ? "query" : "document";
        var outputDataType = resolveOutputDataType(targetType);
        var timeoutMs = calculateTimeoutMs(context);

        var cacheKey = new CacheKey(context.getEmbedderId(), texts, inputType, outputDataType);
        var responseBody = context.computeCachedValueIfAbsent(cacheKey, () -> {
            var jsonRequest = createJsonRequest(texts, inputType, outputDataType);
            log.fine(() -> "VoyageAI request: " + jsonRequest);
            return doRequest(jsonRequest, timeoutMs, context);
        });

        var tensors = toTensors(responseBody, targetType, outputDataType, context);
        var latency = Duration.ofNanos(System.nanoTime() - startTime);
        runtime.sampleEmbeddingLatency(latency.toMillis(), context);
        return tensors;
    }

    private List<Tensor> toTensors(String responseBody, TensorType targetType, String outputDtype, Context context) {
        try {
            Response response;
            List<List<Number>> embeddings;
            if (isContextualModel()) {
                var contextualResponse = objectMapper.readValue(responseBody, ContextualResponse.class);
                response = contextualResponse;
                embeddings = contextualResponse.data.get(0).data.stream()
                        .map(TextEmbeddingData::embedding)
                        .toList();
            } else {
                var voyageResponse = objectMapper.readValue(responseBody, TextResponse.class);
                response = voyageResponse;
                embeddings = voyageResponse.data.stream()
                        .map(TextEmbeddingData::embedding)
                        .toList();
            }
            runtime.sampleSequenceLength(response.usage().totalTokens(), context);
            String dimensionName = targetType.dimensions().get(0).name();
            return embeddings.stream()
                    .map(embedding -> switch (outputDtype) {
                        case "float" -> createFloatTensor(embedding, dimensionName, targetType.valueType());
                        case "int8", "binary" -> createInt8Tensor(embedding, dimensionName);
                        default -> throw new IllegalArgumentException("Unsupported output_dtype: " + outputDtype);
                    })
                    .toList();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse VoyageAI response", e);
        }
    }

    private String createJsonRequest(List<String> texts, String inputType, String outputDataType) {
        Object request;
        if (isMultimodalModel()) {
            request = MultimodalRequest.of(
                    texts.get(0), config.model(), inputType, config.truncate(), config.dimensions(), outputDataType);
        } else if (isContextualModel()) {
            request = ContextualRequest.of(
                    texts, config.model(), inputType, config.dimensions(), outputDataType);
        } else {
            request = TextRequest.of(
                    texts, config.model(), inputType, config.truncate(), config.dimensions(), outputDataType);
        }
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize VoyageAI request", e);
        }
    }

    private String doRequest(String jsonRequest, long timeoutMs, Context context) {
        var httpRequest = new Request.Builder()
                .url(resolvedEndpoint)
                .header("Authorization", "Bearer " + apiKey.current())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonRequest, MediaType.get("application/json; charset=utf-8")))
                .build();

        runtime.sampleRequestCount(context);
        var call = httpClient.newCall(httpRequest);
        call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS);

        try (var response = call.execute()) {
            var responseBody = response.body() != null ? response.body().string() : "";
            log.fine(() -> "VoyageAI response with code " + response.code() + ": " + responseBody);
            if (response.isSuccessful()) {
                return responseBody;
            } else if (response.code() == 429) {
                runtime.sampleRequestFailure(context, response.code());
                throw new OverloadException("VoyageAI API rate limited (429)");
            } else if (response.code() == 401) {
                runtime.sampleRequestFailure(context, response.code());
                throw new RuntimeException("VoyageAI API authentication failed. Please check your API key: " + responseBody);
            } else if (response.code() == 400) {
                runtime.sampleRequestFailure(context, response.code());
                String errorMessage = parseErrorDetail(responseBody).orElse(responseBody);
                throw new InvalidInputException("VoyageAI API bad request (400): " + errorMessage);
            } else {
                runtime.sampleRequestFailure(context, response.code());
                throw new RuntimeException("VoyageAI API request failed with status " + response.code() + ": " + responseBody);
            }
        } catch (InterruptedIOException e) {
            // Covers both OkHttp timeout (InterruptedIOException) and socket timeout (SocketTimeoutException extends InterruptedIOException)
            runtime.sampleRequestFailure(context, 0);
            throw new TimeoutException(
                    "VoyageAI API call timed out after " + timeoutMs + "ms", e);
        } catch (IOException e) {
            runtime.sampleRequestFailure(context, 0);
            throw new RuntimeException("VoyageAI API call failed: " + e.getMessage(), e);
        }
    }

    private Optional<String> parseErrorDetail(String responseBody) {
        try {
            ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
            if (errorResponse.detail != null && !errorResponse.detail.isEmpty()) {
                return Optional.of(errorResponse.detail);
            }
        } catch (JsonProcessingException e) {
            log.fine(() -> "Failed to parse error response as JSON: " + e.getMessage());
        }
        return Optional.empty();
    }

    private long calculateTimeoutMs(Context context) {
        long remainingMs = context.getDeadline()
                .map(d -> d.timeRemaining().toMillis())
                .orElse((long) config.timeout());
        if (remainingMs <= 0)
            throw new TimeoutException("Request deadline exceeded before VoyageAI API call");
        return remainingMs;
    }

    private Tensor createFloatTensor(List<Number> embedding, String dimensionName, TensorType.Value valueType) {
        TensorType type = new TensorType.Builder(valueType)
                .indexed(dimensionName, embedding.size())
                .build();
        IndexedTensor.Builder builder = IndexedTensor.Builder.of(type);
        for (int i = 0; i < embedding.size(); i++) {
            builder.cell(embedding.get(i).floatValue(), i);
        }
        return builder.build();
    }

    private Tensor createInt8Tensor(List<Number> embedding, String dimensionName) {
        TensorType type = new TensorType.Builder(TensorType.Value.INT8)
                .indexed(dimensionName, embedding.size())
                .build();
        IndexedTensor.Builder builder = IndexedTensor.Builder.of(type);
        for (int i = 0; i < embedding.size(); i++) {
            builder.cell(embedding.get(i).byteValue(), i);
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
                        log.fine(() -> Text.format("VoyageAI API server error (%d). Retry %d of %d after %dms", code, retryNumber + 1, maxRetries, RETRY_DELAY_MS));
                        Thread.sleep(RETRY_DELAY_MS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", e);
                }
            }

            var errorMsg = Text.format("Max retries exceeded for VoyageAI API (%d). Last response: %d - %s", maxRetries, lastStatusCode, lastResponseBody);
            throw new IOException(errorMsg);
        }
    }

    // ===== Generic Request/Response DTOs =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(@JsonProperty("total_tokens") int totalTokens) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static abstract class Response {
        @JsonProperty("usage") private Usage usage;
        Usage usage() { return usage; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorResponse(@JsonProperty("detail") String detail) {}

    // ===== Text Request/Response DTOs =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TextRequest(
            @JsonProperty("input") List<String> input,
            @JsonProperty("model") String model,
            @JsonProperty("input_type") String inputType,
            @JsonProperty("truncation") boolean truncation,
            @JsonProperty("output_dimension") @JsonInclude(JsonInclude.Include.NON_NULL) Integer outputDimension,
            @JsonProperty("output_dtype") @JsonInclude(JsonInclude.Include.NON_NULL) String outputDtype) {

        static TextRequest of(List<String> texts, String model, String inputType,
                              boolean truncation, Integer outputDimension, String outputDtype) {
            return new TextRequest(texts, model, inputType, truncation, outputDimension, outputDtype);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TextEmbeddingData(
            @JsonProperty("embedding") List<Number> embedding,
            @JsonProperty("index") int index) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TextResponse extends Response {
        @JsonProperty("data") List<TextEmbeddingData> data;
    }

    // ===== Contextual Request/Response DTOs =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContextualRequest(
            @JsonProperty("inputs") List<List<String>> inputs,
            @JsonProperty("model") String model,
            @JsonProperty("input_type") @JsonInclude(JsonInclude.Include.NON_NULL) String inputType,
            @JsonProperty("output_dimension") @JsonInclude(JsonInclude.Include.NON_NULL) Integer outputDimension,
            @JsonProperty("output_dtype") @JsonInclude(JsonInclude.Include.NON_NULL) String outputDtype) {

        static ContextualRequest of(List<String> texts, String model, String inputType,
                                    Integer outputDimension, String outputDtype) {
            return new ContextualRequest(List.of(texts), model, inputType,
                                         outputDimension, outputDtype);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ContextualResponse extends Response {
        @JsonProperty("data") List<ContextualDocumentResult> data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContextualDocumentResult(@JsonProperty("data") List<TextEmbeddingData> data) {}

    // ===== Multimodal Request DTOs =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MultimodalRequest(
            @JsonProperty("inputs") List<MultimodalInput> inputs,
            @JsonProperty("model") String model,
            @JsonProperty("input_type") @JsonInclude(JsonInclude.Include.NON_NULL) String inputType,
            @JsonProperty("truncation") boolean truncation,
            @JsonProperty("output_dimension") @JsonInclude(JsonInclude.Include.NON_NULL) Integer outputDimension,
            @JsonProperty("output_dtype") @JsonInclude(JsonInclude.Include.NON_NULL) String outputDtype) {

        static MultimodalRequest of(String text, String model, String inputType,
                                    boolean truncation, Integer outputDimension, String outputDtype) {
            return new MultimodalRequest(List.of(MultimodalInput.of(text)), model, inputType,
                                         truncation, outputDimension, outputDtype);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MultimodalInput(@JsonProperty("content") List<MultimodalContentItem> content) {

        static MultimodalInput of(String text) {
            return new MultimodalInput(List.of(MultimodalContentItem.of(text)));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MultimodalContentItem(
            @JsonProperty("type") String type,
            @JsonProperty("text") String text) {

        static MultimodalContentItem of(String text) { return new MultimodalContentItem("text", text); }
    }

    // ===== Cache Key =====

    private record CacheKey(String embedderId, List<String> texts, String inputType, String outputDataType) {}
}
