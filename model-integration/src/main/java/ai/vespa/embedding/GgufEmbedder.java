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

    public static class Exception extends RuntimeException {
        public Exception(Throwable cause) { super(cause); }
    }

    @Inject
    public GgufEmbedder(GgufEmbedderConfig config, ModelPathHelper helper) {
        log.fine(() -> "Config: %s".formatted(config));
        var modelPath = helper.getModelPathResolvingIfNecessary(config.embeddingModelReference()).toString();
        var modelParams = new ModelParameters()
                .enableEmbedding()
                .setModel(modelPath)
                .setCtxSize(config.contextSize())
                .setGpuLayers(config.gpuLayers());
        if (config.continuousBatching()) modelParams.enableContBatching();
        if (config.poolingType() != GgufEmbedderConfig.PoolingType.Enum.UNSPECIFIED)
            modelParams.setPoolingType(PoolingType.valueOf(config.poolingType().name()));
        if (config.physicalMaxBatchSize() >= 0) modelParams.setUbatchSize(config.physicalMaxBatchSize());
        if (config.logicalMaxBatchSize() >= 0) modelParams.setBatchSize(config.logicalMaxBatchSize());
        model = new LlamaModel(modelParams);
    }

    @Override
    public Tensor embed(String text, Context context, TensorType tensorType) {
        record CacheKey(String embedderId, String text){}
        var cacheKey = new CacheKey(context.getEmbedderId(), text);
        var rawEmbedding = context.computeCachedValueIfAbsent(cacheKey, () -> wrapLlamaException(() -> model.embed(text)));
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

    private static <T> T wrapLlamaException(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw new Exception(e);
        }
    }
}
