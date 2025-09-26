// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.modelintegration.utils.ModelPathHelper;
import ai.vespa.modelintegration.utils.OnnxExternalDataResolver;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.language.huggingface.HuggingFaceTokenizer;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.Tensors;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static com.yahoo.language.huggingface.ModelInfo.TruncationStrategy.LONGEST_FIRST;

@Beta
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
    public HuggingFaceEmbedder(OnnxRuntime onnx, Embedder.Runtime runtime, HuggingFaceEmbedderConfig config, ModelPathHelper modelHelper) {
        this.runtime = runtime;
        var optionsBuilder = new OnnxEvaluatorOptions.Builder()
                .setExecutionMode(config.transformerExecutionMode().toString())
                .setThreads(config.transformerInterOpThreads(), config.transformerIntraOpThreads());
        if (config.transformerGpuDevice() >= 0)
            optionsBuilder.setGpuDevice(config.transformerGpuDevice());

        var onnxOpts = optionsBuilder.build();
        var resolver = new OnnxExternalDataResolver(modelHelper);
        evaluator = onnx.evaluatorOf(resolver.resolveOnnxModel(config.transformerModelReference()).toString(), onnxOpts);

        this.analysis = analyze(evaluator, config);
        normalize = config.normalize();
        prependQuery = config.prependQuery();
        prependDocument = config.prependDocument();
        var tokenizerPath = modelHelper.getModelPathResolvingIfNecessary(config.tokenizerPathReference());
        var builder = new HuggingFaceTokenizer.Builder()
                .addSpecialTokens(true)
                .addDefaultModel(tokenizerPath)
                .setPadding(false);
        var info = HuggingFaceTokenizer.getModelInfo(tokenizerPath);
        log.fine(() -> "'%s' has info '%s'".formatted(tokenizerPath, info));
        if (info.maxLength() == -1 || info.truncation() != LONGEST_FIRST) {
            // Force truncation to max token vector length accepted by model if tokenizer.json contains no valid truncation configuration
            int maxLength = info.maxLength() > 0 && info.maxLength() <= config.transformerMaxTokens()
                    ? info.maxLength()
                    : config.transformerMaxTokens();
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
        IndexedTensor tokenEmbeddings = embeddingResult.output;
        if (targetType.valueType() == TensorType.Value.INT8) {
            return binaryQuantization(embeddingResult, targetType);
        } else {
            Tensor result = analysis.poolingStrategy.toSentenceEmbedding(targetType, tokenEmbeddings, embeddingResult.attentionMask);
            return normalize ? EmbeddingNormalizer.normalize(result, targetType) : result;
        }
    }

    String prependInstruction(String text, Context context) {
        if (prependQuery != null && !prependQuery.isEmpty() && context.getDestination().startsWith("query")) {
            return prependQuery + " " + text;
        }
        if (prependDocument != null && !prependDocument.isEmpty()){
            return prependDocument + " " + text;
        }
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

    private Tensor binaryQuantization(HuggingFaceEmbedder.HFEmbeddingResult embeddingResult, TensorType targetType) {
        long outputDimensions = embeddingResult.output().shape()[2];
        long targetDimensions = targetType.dimensions().get(0).size().get();
        //🪆 flexibility - packing only the first 8*targetDimension float values from the model output
        long targetUnpackagedDimensions = 8 * targetDimensions;
        if (targetUnpackagedDimensions > outputDimensions) {
            throw new IllegalArgumentException("Cannot pack " + outputDimensions + " into " + targetDimensions + " int8's");
        }
        // pool and normalize using float version before binary quantization
        TensorType poolingType = new TensorType.Builder(TensorType.Value.FLOAT).
                                         indexed(targetType.indexedSubtype().dimensions().get(0).name(), targetUnpackagedDimensions)
                                         .build();
        Tensor result = analysis.poolingStrategy().toSentenceEmbedding(poolingType, embeddingResult.output(), embeddingResult.attentionMask());
        result = normalize ? EmbeddingNormalizer.normalize(result, poolingType) : result;
        Tensor packedResult = Tensors.packBits(result);
        if ( ! packedResult.type().equals(targetType))
            throw new IllegalStateException("Expected pack_bits to produce " + targetType + ", but got " + packedResult.type());
        return packedResult;
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
