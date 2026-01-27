// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.modelintegration.utils.ModelPathHelper;
import ai.vespa.modelintegration.utils.OnnxExternalDataResolver;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.language.huggingface.HuggingFaceTokenizer;
import com.yahoo.language.process.Embedder;
import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.Tensors;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static com.yahoo.language.huggingface.ModelInfo.TruncationStrategy.LONGEST_FIRST;

/**
 * A general embedder for HuggingFace models.
 * This will also quantize to the target embedding type.
 */
public class HuggingFaceEmbedder extends AbstractComponent implements Embedder {

    private static final Logger log = Logger.getLogger(HuggingFaceEmbedder.class.getName());

    private final Embedder.Runtime runtime;
    private final ModelAnalysis analysis;
    private final boolean normalize;
    private final HuggingFaceTokenizer tokenizer;
    private final OnnxEvaluator evaluator;
    private final String prependQuery;
    private final String prependDocument;

    record ModelAnalysis(int numInputs,
                         String inputIdsName,
                         String attentionMaskName,
                         String tokenTypeIdsName,
                         String outputName,
                         int outputDimensions,
                         PoolingStrategy poolingStrategy)
    {
        boolean useAttentionMask() { return ! attentionMaskName.isEmpty(); }
        boolean useTokenTypeIds() { return ! tokenTypeIdsName.isEmpty(); }
    }

    static ModelAnalysis analyze(OnnxEvaluator evaluator, HuggingFaceEmbedderConfig config) {
        Map<String, TensorType> inputs = evaluator.getInputInfo();
        int numInputs = inputs.size();
        String inputIdsName = config.transformerInputIds();
        String attentionMaskName = "";
        String tokenTypeIdsName = "";
        validateName(inputs, inputIdsName, "input");
        // some new models have only 1 input
        if (numInputs > 1) {
            attentionMaskName = config.transformerAttentionMask();
            validateName(inputs, attentionMaskName, "input");
            // newer models have only 2 inputs (they do not use token type IDs)
            if (numInputs > 2) {
                tokenTypeIdsName = config.transformerTokenTypeIds();
                validateName(inputs, tokenTypeIdsName, "input");
                if (numInputs > 3) {
                    throw new IllegalArgumentException("Model needs more than 3 inputs: " + inputs.keySet());
                }
            }
        }
        Map<String, TensorType> outputs = evaluator.getOutputInfo();
        String outputName = config.transformerOutput();
        validateName(outputs, outputName, "output");
        int outputDimensions = outputs.get(outputName).dimensions().size();
        var poolingStrategy = PoolingStrategy.fromString(config.poolingStrategy().toString());
        if (outputDimensions == 2) {
            if (poolingStrategy != PoolingStrategy.NONE) {
                throw new IllegalArgumentException("Expected pooling-strategy 'none' with 2 output dimensions");
            }
        } else if (outputDimensions == 3) {
            if (poolingStrategy == PoolingStrategy.NONE) {
                throw new IllegalArgumentException("Unexpected pooling-strategy 'none' with 3 output dimensions");
            }
        } else {
            throw new IllegalArgumentException("Expected 2 or 3 output dimensions for '" + outputName + "', but got type: " + outputs.get(outputName));
        }
        return new ModelAnalysis(numInputs,
                                 inputIdsName,
                                 attentionMaskName,
                                 tokenTypeIdsName,
                                 outputName,
                                 outputDimensions,
                                 poolingStrategy);
    }

    @Inject
    public HuggingFaceEmbedder(OnnxRuntime onnx, Embedder.Runtime runtime, HuggingFaceEmbedderConfig embedderConfig, OnnxEvaluatorConfig onnxConfig, ModelPathHelper modelHelper) {
        this.runtime = runtime;
        
        var resolver = new OnnxExternalDataResolver(modelHelper);
        var modelPath = resolver.resolveOnnxModel(embedderConfig.transformerModelReference()).toString();
        var onnxOpts = OnnxEvaluatorOptions.of(onnxConfig);
        evaluator = onnx.evaluatorOf(modelPath, onnxOpts);
        
        this.analysis = analyze(evaluator, embedderConfig);
        normalize = embedderConfig.normalize();
        prependQuery = embedderConfig.prependQuery();
        prependDocument = embedderConfig.prependDocument();
        var tokenizerPath = modelHelper.getModelPathResolvingIfNecessary(embedderConfig.tokenizerPathReference());
        var builder = new HuggingFaceTokenizer.Builder()
                .addSpecialTokens(true)
                .addDefaultModel(tokenizerPath)
                .setPadding(false);
        var info = HuggingFaceTokenizer.getModelInfo(tokenizerPath);
        log.fine(() -> "'%s' has info '%s'".formatted(tokenizerPath, info));
        if (info.maxLength() == -1 || info.truncation() != LONGEST_FIRST) {
            // Force truncation to max token vector length accepted by model if tokenizer.json contains no valid truncation configuration
            int maxLength = info.maxLength() > 0 && info.maxLength() <= embedderConfig.transformerMaxTokens()
                    ? info.maxLength()
                    : embedderConfig.transformerMaxTokens();
            builder.setTruncation(true).setMaxLength(maxLength);
        }
        this.tokenizer = builder.build();
    }

    private static void validateName(Map<String, TensorType> types, String name, String type) {
        if ( ! types.containsKey(name)) {
            throw new IllegalArgumentException("Model does not contain required " + type + ": '" + name + "'. " +
                                               "Model contains: " + String.join(",", types.keySet()));
        }
    }

    @Override
    public List<Integer> embed(String s, Context context) {
        var start = System.nanoTime();
        var tokens = tokenizer.embed(s, context);
        runtime.sampleSequenceLength(tokens.size(), context);
        runtime.sampleEmbeddingLatency((System.nanoTime() - start)/1_000_000d, context);
        return tokens;
    }

    @Override
    public void deconstruct() {
        evaluator.close();
        tokenizer.close();
    }

    @Override
    public Tensor embed(String text, Context context, TensorType targetType) {
        if (targetType.dimensions().size() != 1) {
            throw new IllegalArgumentException("Error in embedding to type '" + targetType + "': should only have one dimension.");
        }
        if (!targetType.dimensions().get(0).isIndexed()) {
            throw new IllegalArgumentException("Error in embedding to type '" + targetType + "': dimension should be indexed.");
        }
        var embeddingResult = lookupOrEvaluate(context, prependInstruction(text, context));
        if (targetType.valueType() == TensorType.Value.INT8 && sizeIndicatesBitPacking(targetType, embeddingResult)) {
            return binaryQuantize(embeddingResult, targetType);
        } else if (targetType.valueType() == TensorType.Value.INT8) {
            return byteQuantize(embeddingResult, targetType);
        } else {
            return poolAndNormalize(embeddingResult, targetType, targetType.dimensions().get(0).size().get());
        }
    }

    private boolean sizeIndicatesBitPacking(TensorType targetType, HuggingFaceEmbedder.HFEmbeddingResult embeddingResult) {
        return targetType.dimensions().get(0).size().get()
               <= embeddingResult.output().shape()[embeddingResult.output().shape().length - 1] / 8;
    }

    String prependInstruction(String text, Context context) {
        if (prependQuery != null && !prependQuery.isEmpty() && context.getDestination().startsWith("query"))
            return prependQuery + " " + text;
        if (prependDocument != null && !prependDocument.isEmpty())
            return prependDocument + " " + text;
        return text;
    }

    private HuggingFaceEmbedder.HFEmbeddingResult lookupOrEvaluate(Context context, String text) {
        var key = new HFEmbedderCacheKey(context.getEmbedderId(), text);
        return context.computeCachedValueIfAbsent(key, () -> evaluate(context, text));
    }

    private HuggingFaceEmbedder.HFEmbeddingResult evaluate(Context context, String text) {
        var start = System.nanoTime();
        var encoding = tokenizer.encode(text, context.getLanguage());
        runtime.sampleSequenceLength(encoding.ids().size(), context);
        Tensor inputSequence = createTensorRepresentation(encoding.ids(), "d1").expand("d0");
        Tensor attentionMask = createTensorRepresentation(encoding.attentionMask(), "d1").expand("d0");
        Map<String, Tensor> inputs;
        if (analysis.useAttentionMask()) {
             if (analysis.useTokenTypeIds()) {
                 Tensor tokenTypeIds = createTensorRepresentation(encoding.typeIds(), "d1").expand("d0");
                 inputs = Map.of(analysis.inputIdsName(), inputSequence,
                                 analysis.attentionMaskName(), attentionMask,
                                 analysis.tokenTypeIdsName(), tokenTypeIds);
             } else {
                 inputs = Map.of(analysis.inputIdsName(), inputSequence,
                                 analysis.attentionMaskName, attentionMask);
             }
        } else {
            inputs = Map.of(analysis.inputIdsName(), inputSequence);
        }
        IndexedTensor tokenEmbeddings = (IndexedTensor) evaluator.evaluate(inputs).get(analysis.outputName());
        long[] resultShape = tokenEmbeddings.shape();
        // shape should have batch, sequence, embedding dimensionality
        if (resultShape.length != analysis.outputDimensions()) {
            throw new IllegalArgumentException("Expected " + analysis.outputDimensions + " output dimensions for output name '" +
                                               analysis.outputName() + "': [batch, sequence, embedding], got " + resultShape.length);
        }
        runtime.sampleEmbeddingLatency((System.nanoTime() - start)/1_000_000d, context);
        return new HFEmbeddingResult(tokenEmbeddings, attentionMask, context.getEmbedderId());
    }

    private Tensor binaryQuantize(HuggingFaceEmbedder.HFEmbeddingResult embeddingResult, TensorType targetType) {
        long targetUnpackagedDimensions = 8 * targetType.dimensions().get(0).size().get();
        Tensor packedResult = Tensors.packBits(poolAndNormalize(embeddingResult, targetType, targetUnpackagedDimensions));
        if ( ! packedResult.type().equals(targetType))
            throw new IllegalStateException("Expected pack_bits to produce " + targetType + ", but got " + packedResult.type());
        return packedResult;
    }

    private Tensor byteQuantize(HuggingFaceEmbedder.HFEmbeddingResult embeddingResult, TensorType targetType) {
        long targetDimensions = targetType.dimensions().get(0).size().get();
        var result = (IndexedTensor)poolAndNormalize(embeddingResult, targetType, targetDimensions);
        IndexedTensor.Builder builder = IndexedTensor.Builder.of(targetType);
        for (int i = 0; i < targetDimensions; i++) {
            double value = result.get(i);
            int quantized = (int) Math.round(value * 127.0);      // scale to byte
            quantized = Math.max(-128, Math.min(127, quantized)); // clamp
            builder.cell((byte) quantized, i);
        }
        return builder.build();
    }

    private Tensor poolAndNormalize(HuggingFaceEmbedder.HFEmbeddingResult embeddingResult, TensorType targetType, long targetDimensions) {
        long outputDimensions = embeddingResult.output().shape()[embeddingResult.output().shape().length - 1];
        if (targetDimensions > outputDimensions)
            throw new IllegalArgumentException("Cannot quantize " + outputDimensions + " dimensions into " + targetType);

        TensorType poolingType = new TensorType.Builder(TensorType.Value.FLOAT).
                                         indexed(targetType.indexedSubtype().dimensions().get(0).name(), targetDimensions)
                                         .build();
        Tensor result = analysis.poolingStrategy().toSentenceEmbedding(poolingType, embeddingResult.output(), embeddingResult.attentionMask());
        return normalize ? TensorNormalizer.normalize(result) : result;
    }

    private IndexedTensor createTensorRepresentation(List<Long> input, String dimension) {
        int size = input.size();
        TensorType type = new TensorType.Builder(TensorType.Value.FLOAT).indexed(dimension, size).build();
        IndexedTensor.Builder builder = IndexedTensor.Builder.of(type);
        for (int i = 0; i < size; ++i) {
            builder.cell(input.get(i), i);
        }
        return builder.build();
    }

    protected record HFEmbeddingResult(IndexedTensor output, Tensor attentionMask, String embedderId) {}
    protected record HFEmbedderCacheKey(String embedderId, Object embeddedValue) { }

}
