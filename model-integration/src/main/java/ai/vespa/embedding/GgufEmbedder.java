// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.GgufEmbedderConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.args.PoolingType;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Embedder using GGUF file format through llama.cpp
 *
 * @author bjorncs
 */
public class GgufEmbedder extends AbstractComponent implements Embedder {

    private static final Logger log = Logger.getLogger(GgufEmbedder.class.getName());
    private final LlamaModel model;
    private final int maxPromptTokens;

    public static class Exception extends RuntimeException {
        public Exception(Throwable cause) { super(cause); }
    }

    @Inject
    public GgufEmbedder(GgufEmbedderConfig config, ModelPathHelper helper) {
        log.fine(() -> "Config: %s".formatted(config));
        var modelPath = helper.getModelPathResolvingIfNecessary(config.embeddingModelReference()).toString();
        var modelParams = new ModelParameters()
                .enableEmbedding()
                .disableLog()
                .setModel(modelPath)
                .setCtxSize(config.contextSize())
                .setGpuLayers(config.gpuLayers());
        if (config.continuousBatching()) modelParams.enableContBatching();
        if (config.poolingType() != GgufEmbedderConfig.PoolingType.Enum.UNSPECIFIED)
            modelParams.setPoolingType(PoolingType.valueOf(config.poolingType().name()));
        if (config.physicalMaxBatchSize() > 0) modelParams.setUbatchSize(config.physicalMaxBatchSize());
        if (config.logicalMaxBatchSize() > 0) modelParams.setBatchSize(config.logicalMaxBatchSize());
        if (config.contextSize() > 0) modelParams.setCtxSize(config.contextSize());
        model = new LlamaModel(modelParams);
        maxPromptTokens = config.maxPromptTokens();
    }

    @Override
    public Tensor embed(String text, Context context, TensorType tensorType) {
        var prompt = truncatePrompt(text);
        record CacheKey(String embedderId, String text){}
        var cacheKey = new CacheKey(context.getEmbedderId(), prompt);
        var rawEmbedding = context.computeCachedValueIfAbsent(cacheKey, () -> generateRawEmbedding(prompt));
        if (tensorType.dimensions().size() != 1) {
            throw new IllegalArgumentException(
                    "Error in embedding to type '%s': should only have one dimension.".formatted(tensorType));
        }
        var dimension = tensorType.dimensions().get(0);
        if (!dimension.isIndexed()) {
            throw new IllegalArgumentException(
                    "Error in embedding to type '%s': dimension should be indexed.".formatted(tensorType));
        }
        var dimensionSize = dimension.size().orElseThrow();
        if (rawEmbedding.length != dimensionSize) {
            throw new IllegalArgumentException(
                    "Error in embedding to type '%s': expected dimension size %d, but got %d.".formatted(
                            tensorType, dimensionSize, rawEmbedding.length));
        }
        var builder = Tensor.Builder.of(tensorType);
        for (int i = 0; i < dimensionSize; i++) {
            builder.cell(rawEmbedding[i], i);
        }
        return builder.build();
    }

    @Override
    public List<Integer> embed(String text, Context context) {
        return Arrays.stream(wrapLlamaException(() -> model.encode(text)))
                .boxed().toList();
    }

    @Override
    public String decode(List<Integer> tokens, Context context) {
        return wrapLlamaException(() -> model.decode(tokens.stream().mapToInt(Integer::intValue).toArray()));
    }

    @Override
    public void deconstruct() {
        model.close();
    }

    /**
     * Performs a naive truncation of the prompt to the maximum number of tokens.
     * Tokens are assumed to be independent and that the token sequence can be safely truncated at any position.
     */
    private String truncatePrompt(String text) {
        if (maxPromptTokens <= 0) return text;
        var tokens = model.encode(text);
        var maxTruncatedLength = maxPromptTokens - 2; // Reserve space for start and end token
        if (tokens.length <= maxTruncatedLength) return text;
        log.fine(() -> "Truncating prompt from %d to %d tokens".formatted(tokens.length, maxTruncatedLength));
        var truncatedTokens = Arrays.copyOfRange(tokens, 0, maxTruncatedLength);
        return model.decode(truncatedTokens);
    }

    private float[] generateRawEmbedding(String prompt) {
        try {
            return wrapLlamaException(() -> model.embed(prompt));
        } catch (GgufEmbedder.Exception e) {
            var cause = e.getCause();
            if (cause == null) throw e;
            if (cause.getClass().getName().endsWith("de.kherud.llama.LlamaException") // Package-private exception
                    && cause.getMessage().contains("input is too large to process")) {
                // Illegal input must be propagated as IllegalArgumentException
                throw new IllegalArgumentException(
                        "Input text is too large (prompt UTF-16 length: %d). Either set max prompt tokens or adjust batch/context size."
                                .formatted(prompt.length()),
                        cause);
            }
            throw e;
        }
    }

    private static <T> T wrapLlamaException(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw new GgufEmbedder.Exception(e);
        }
    }
}
