// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.HttpEmbedderConfig;
import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Embedder using the VoyageAI embeddings API. Auto-selects between the text, multimodal,
 * and contextualized endpoints based on the configured model name.
 *
 * @see <a href="https://docs.voyageai.com/">VoyageAI Documentation</a>
 * @author bjorncs
 */
@Beta
public class VoyageAIEmbedder extends AbstractHttpEmbedder implements Embedder {

    private static final String EMBEDDINGS_ENDPOINT = "https://api.voyageai.com/v1/embeddings";
    private static final String MULTIMODAL_EMBEDDINGS_ENDPOINT = "https://api.voyageai.com/v1/multimodalembeddings";
    private static final String CONTEXTUALIZED_EMBEDDINGS_ENDPOINT = "https://api.voyageai.com/v1/contextualizedembeddings";

    private final VoyageAiEmbedderConfig config;
    private final Embedder.Runtime runtime;
    private final Secret apiKey;
    private final Embedder.Batching batching;
    private final EmbeddingQuantization.Quantization quantization;
    private final String resolvedEndpoint;
    private final boolean isMultimodal;
    private final boolean isContextual;

    @Inject
    public VoyageAIEmbedder(VoyageAiEmbedderConfig config, Embedder.Runtime runtime, Secrets secrets) {
        this(config, new HttpEmbedderConfig.Builder().build(), runtime, secrets);
    }

    VoyageAIEmbedder(VoyageAiEmbedderConfig config, HttpEmbedderConfig httpConfig,
                     Embedder.Runtime runtime, Secrets secrets) {
        super(httpConfig);
        this.config = config;
        this.runtime = runtime;
        if (config.apiKeySecretRef().isBlank())
            throw new IllegalArgumentException("'api-key-secret-ref' must be configured for VoyageAI embedder");
        this.apiKey = secrets.get(config.apiKeySecretRef());
        this.batching = Embedder.Batching.of(
                config.batching().maxSize(), Duration.ofMillis(config.batching().maxDelayMillis()));
        this.quantization = EmbeddingQuantization.Quantization.valueOf(config.quantization().name());
        this.isMultimodal = config.model().startsWith("voyage-multimodal-");
        this.isContextual = config.model().startsWith("voyage-context-");
        this.resolvedEndpoint = resolveEndpoint(config.endpoint(), isMultimodal, isContextual);
    }

    @Override public Batching batchingConfig() { return (isMultimodal || isContextual) ? Batching.DISABLED : batching; }

    @Override
    public List<Integer> embed(String text, Context context) {
        throw new UnsupportedOperationException(
                "VoyageAI embedder only supports embed() with TensorType. Use embed(String, Context, TensorType) instead.");
    }

    @Override
    public Tensor embed(String text, Context context, TensorType targetType) {
        return embed(List.of(text), context, targetType).get(0);
    }

    @Override
    public List<Tensor> embed(List<String> texts, Context context, TensorType targetType) {
        long startTime = System.nanoTime();
        EmbeddingQuantization.validateTensorType(targetType, config.dimensions(), quantization);
        var inputType = context.getDestinationType() == Context.DestinationType.QUERY ? "query" : "document";
        var outputDataType = EmbeddingQuantization.resolveOutputDataType(targetType, config.dimensions(), quantization);

        record CacheKey(String embedderId, List<String> texts, String inputType, String outputDataType) {}
        var cacheKey = new CacheKey(context.getEmbedderId(), texts, inputType, outputDataType);
        var response = context.computeCachedValueIfAbsent(
                cacheKey, () -> sendRequest(texts, inputType, outputDataType, context));

        if (response.totalTokens() > 0)
            runtime.sampleSequenceLength(response.totalTokens(), context);
        var tensors = toTensors(response, targetType, outputDataType);
        runtime.sampleEmbeddingLatency(Duration.ofNanos(System.nanoTime() - startTime).toMillis(), context);
        return tensors;
    }

    private VoyageResponse sendRequest(List<String> texts, String inputType, String outputDataType, Context context) {
        var json = buildRequestJson(texts, inputType, outputDataType);
        runtime.sampleRequestCount(context);
        var body = doHttpRequest(resolvedEndpoint, json, authHeaders(), context, runtime);
        return parseSuccess(body);
    }

    private String buildRequestJson(List<String> texts, String inputType, String outputDataType) {
        Object request;
        if (isMultimodal) {
            // Multimodal API uses a different input structure (content items with type+text/image);
            // batching is disabled so only a single text is expected here
            if (texts.size() != 1)
                throw new IllegalArgumentException("Multimodal models do not support batching");
            request = MultimodalRequest.of(
                    texts.get(0), config.model(), inputType, config.truncate(), config.dimensions(), outputDataType);
        } else if (isContextual) {
            // Contextual API treats the text list as chunks of a single document; batching is
            // disabled to prevent cross-document context contamination from independent embed()
            // calls being combined by the framework
            request = ContextualRequest.of(
                    texts, config.model(), inputType, config.dimensions(), outputDataType);
        } else {
            request = TextRequest.of(
                    texts, config.model(), inputType, config.truncate(), config.dimensions(), outputDataType);
        }
        return toJson(request);
    }

    private VoyageResponse parseSuccess(String body) {
        List<String> encoded;
        int totalTokens;
        if (isContextual) {
            var response = fromJson(body, ContextualResponse.class);
            totalTokens = response.usage != null ? response.usage.totalTokens() : 0;
            encoded = response.data.get(0).data.stream()
                    .sorted(Comparator.comparingInt(TextEmbeddingData::index))
                    .map(TextEmbeddingData::embedding)
                    .toList();
        } else {
            var response = fromJson(body, TextResponse.class);
            totalTokens = response.usage != null ? response.usage.totalTokens() : 0;
            encoded = response.data.stream()
                    .sorted(Comparator.comparingInt(TextEmbeddingData::index))
                    .map(TextEmbeddingData::embedding)
                    .toList();
        }
        return new VoyageResponse(encoded, totalTokens);
    }

    private static String resolveEndpoint(String configured, boolean isMultimodal, boolean isContextual) {
        if (!configured.equals(EMBEDDINGS_ENDPOINT)) return configured;
        if (isMultimodal) return MULTIMODAL_EMBEDDINGS_ENDPOINT;
        if (isContextual) return CONTEXTUALIZED_EMBEDDINGS_ENDPOINT;
        return EMBEDDINGS_ENDPOINT;
    }

    private Map<String, String> authHeaders() {
        return Map.of("Authorization", "Bearer " + apiKey.current());
    }

    private static List<Tensor> toTensors(VoyageResponse response, TensorType targetType, String outputDataType) {
        var dim = targetType.dimensions().get(0);
        long expectedDimensions = dim.size().orElseThrow();
        return response.encodedEmbeddings().stream()
                .map(encoded -> switch (outputDataType) {
                    case "float" -> EmbeddingQuantization.decodeBase64FloatTensor(encoded, dim.name(), targetType.valueType(), expectedDimensions);
                    case "int8", "binary" -> EmbeddingQuantization.decodeBase64Int8Tensor(encoded, dim.name(), expectedDimensions);
                    default -> throw new IllegalArgumentException("Unsupported output_dtype: " + outputDataType);
                })
                .toList();
    }


    // ===== Normalized internal response =====

    private record VoyageResponse(List<String> encodedEmbeddings, int totalTokens) {}

    // ===== DTOs =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(@JsonProperty("total_tokens") int totalTokens) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TextRequest(
            @JsonProperty("input") List<String> input,
            @JsonProperty("model") String model,
            @JsonProperty("input_type") String inputType,
            @JsonProperty("truncation") boolean truncation,
            @JsonProperty("output_dimension") @JsonInclude(JsonInclude.Include.NON_NULL) Integer outputDimension,
            @JsonProperty("output_dtype") @JsonInclude(JsonInclude.Include.NON_NULL) String outputDtype,
            @JsonProperty("encoding_format") String encodingFormat) {

        static TextRequest of(List<String> texts, String model, String inputType,
                              boolean truncation, int outputDimension, String outputDtype) {
            return new TextRequest(texts, model, inputType, truncation, outputDimension, outputDtype, "base64");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TextEmbeddingData(
            @JsonProperty("embedding") String embedding,
            @JsonProperty("index") int index) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TextResponse {
        @JsonProperty("data") List<TextEmbeddingData> data;
        @JsonProperty("usage") Usage usage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContextualRequest(
            @JsonProperty("inputs") List<List<String>> inputs,
            @JsonProperty("model") String model,
            @JsonProperty("input_type") @JsonInclude(JsonInclude.Include.NON_NULL) String inputType,
            @JsonProperty("output_dimension") @JsonInclude(JsonInclude.Include.NON_NULL) Integer outputDimension,
            @JsonProperty("output_dtype") @JsonInclude(JsonInclude.Include.NON_NULL) String outputDtype,
            @JsonProperty("encoding_format") String encodingFormat) {

        static ContextualRequest of(List<String> texts, String model, String inputType,
                                    int outputDimension, String outputDtype) {
            return new ContextualRequest(List.of(texts), model, inputType, outputDimension, outputDtype, "base64");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ContextualResponse {
        @JsonProperty("data") List<ContextualDocumentResult> data;
        @JsonProperty("usage") Usage usage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContextualDocumentResult(@JsonProperty("data") List<TextEmbeddingData> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MultimodalRequest(
            @JsonProperty("inputs") List<MultimodalInput> inputs,
            @JsonProperty("model") String model,
            @JsonProperty("input_type") @JsonInclude(JsonInclude.Include.NON_NULL) String inputType,
            @JsonProperty("truncation") boolean truncation,
            @JsonProperty("output_dimension") @JsonInclude(JsonInclude.Include.NON_NULL) Integer outputDimension,
            @JsonProperty("output_dtype") @JsonInclude(JsonInclude.Include.NON_NULL) String outputDtype,
            @JsonProperty("encoding_format") String encodingFormat) {

        static MultimodalRequest of(String text, String model, String inputType,
                                    boolean truncation, int outputDimension, String outputDtype) {
            return new MultimodalRequest(List.of(MultimodalInput.of(text)), model, inputType,
                                         truncation, outputDimension, outputDtype, "base64");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MultimodalInput(@JsonProperty("content") List<MultimodalContentItem> content) {
        static MultimodalInput of(String text) { return new MultimodalInput(List.of(MultimodalContentItem.of(text))); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MultimodalContentItem(
            @JsonProperty("type") String type,
            @JsonProperty("text") String text) {
        static MultimodalContentItem of(String text) { return new MultimodalContentItem("text", text); }
    }

}
