// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.HttpEmbedderConfig;
import ai.vespa.embedding.config.MistralEmbedderConfig;
import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
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
 * Embedder using the Mistral embeddings API.
 *
 * @see <a href="https://docs.mistral.ai/api/endpoint/embeddings">Mistral Embeddings API</a>
 * @author bjorncs
 */
@Beta
public class MistralEmbedder extends AbstractHttpEmbedder implements Embedder {

    private final MistralEmbedderConfig config;
    private final Embedder.Runtime runtime;
    private final Secret apiKey;
    private final EmbeddingQuantization.Quantization quantization;

    @Inject
    public MistralEmbedder(MistralEmbedderConfig config, Embedder.Runtime runtime, Secrets secrets) {
        this(config, new HttpEmbedderConfig.Builder().build(), runtime, secrets);
    }

    MistralEmbedder(MistralEmbedderConfig config, HttpEmbedderConfig httpConfig,
                    Embedder.Runtime runtime, Secrets secrets) {
        super(httpConfig);
        this.config = config;
        this.runtime = runtime;
        this.apiKey = secrets.get(config.apiKeySecretRef());
        this.quantization = EmbeddingQuantization.Quantization.valueOf(config.quantization().name());
    }

    @Override
    public List<Integer> embed(String text, Context context) {
        throw new UnsupportedOperationException(
                "Mistral embedder only supports embed() with TensorType. Use embed(String, Context, TensorType) instead.");
    }

    @Override
    public Tensor embed(String text, Context context, TensorType targetType) {
        return embed(List.of(text), context, targetType).get(0);
    }

    @Override
    public List<Tensor> embed(List<String> texts, Context context, TensorType targetType) {
        long startTime = System.nanoTime();
        EmbeddingQuantization.validateTensorType(targetType, config.dimensions(), quantization);
        var outputDataType = EmbeddingQuantization.resolveOutputDataType(targetType, config.dimensions(), quantization);

        record CacheKey(String embedderId, List<String> texts, String outputDataType) {}
        var cacheKey = new CacheKey(context.getEmbedderId(), texts, outputDataType);
        var response = context.computeCachedValueIfAbsent(cacheKey, () -> sendRequest(texts, outputDataType, context));

        if (response.usage != null) runtime.sampleSequenceLength(response.usage.totalTokens(), context);
        var tensors = toTensors(response, targetType, outputDataType);
        runtime.sampleEmbeddingLatency(Duration.ofNanos(System.nanoTime() - startTime).toMillis(), context);
        return tensors;
    }

    private EmbeddingResponse sendRequest(List<String> texts, String outputDataType, Context context) {
        var outputDimension = hasConfigurableDimensions() ? config.dimensions() : null;
        var json = toJson(new EmbeddingRequest(texts, config.model(), outputDimension, outputDataType));
        runtime.sampleRequestCount(context);
        var body = doHttpRequest(config.endpoint(), json, authHeaders(), context, runtime);
        return fromJson(body, EmbeddingResponse.class);
    }

    private Map<String, String> authHeaders() {
        return Map.of("Authorization", "Bearer " + apiKey.current());
    }

    private static List<Tensor> toTensors(EmbeddingResponse response, TensorType targetType, String outputDataType) {
        var dimensionName = targetType.dimensions().get(0).name();
        return response.data.stream()
                .sorted(Comparator.comparingInt(EmbeddingData::index))
                .map(d -> switch (outputDataType) {
                    case "float" -> EmbeddingQuantization.decodeJsonArrayFloatTensor(d.embedding(), dimensionName, targetType.valueType());
                    case "int8", "binary" -> EmbeddingQuantization.decodeJsonArrayInt8Tensor(d.embedding(), dimensionName);
                    default -> throw new IllegalArgumentException("Unsupported output_dtype: " + outputDataType);
                })
                .toList();
    }

    /** Only newer models (e.g. codestral-embed) support configurable dimensions; mistral-embed does not. */
    private boolean hasConfigurableDimensions() {
        return !config.model().startsWith("mistral-embed");
    }

    // ===== DTOs =====

    private record EmbeddingRequest(
            @JsonProperty("input") List<String> input,
            @JsonProperty("model") String model,
            @JsonProperty("output_dimension") @JsonInclude(JsonInclude.Include.NON_NULL) Integer outputDimension,
            @JsonProperty("output_dtype") String outputDtype) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(
            @JsonProperty("data") List<EmbeddingData> data,
            @JsonProperty("usage") Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingData(
            @JsonProperty("embedding") JsonNode embedding,
            @JsonProperty("index") int index) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(@JsonProperty("total_tokens") int totalTokens) {}

}
