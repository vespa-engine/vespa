// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding.huggingface;

import ai.vespa.embedding.PoolingStrategy;
import ai.vespa.embedding.support.ModelPathHelper;
import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.language.huggingface.HuggingFaceTokenizer;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.Tensors;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static com.yahoo.language.huggingface.ModelInfo.TruncationStrategy.LONGEST_FIRST;

@Beta
public class HuggingFaceEmbedder extends AbstractComponent implements Embedder {

    private static final Logger log = Logger.getLogger(HuggingFaceEmbedder.class.getName());

    private final Embedder.Runtime runtime;
    private final String inputIdsName;
    private final String attentionMaskName;
    private final String tokenTypeIdsName;
    private final String outputName;
    private final boolean normalize;
    private final HuggingFaceTokenizer tokenizer;
    private final OnnxEvaluator evaluator;
    private final PoolingStrategy poolingStrategy;

    private final String prependQuery;

    private final String prependDocument;

    @Inject
    public HuggingFaceEmbedder(OnnxRuntime onnx, Embedder.Runtime runtime, HuggingFaceEmbedderConfig config, ModelPathHelper modelHelper) {
        this.runtime = runtime;
        inputIdsName = config.transformerInputIds();
        attentionMaskName = config.transformerAttentionMask();
        outputName = config.transformerOutput();
        normalize = config.normalize();
        prependQuery = config.prependQuery();
        prependDocument = config.prependDocument();
        var tokenizerPath = Paths.get(modelHelper.getModelPathResolvingIfNecessary(config.tokenizerPathReference()));
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
        poolingStrategy = PoolingStrategy.fromString(config.poolingStrategy().toString());
        var optionsBuilder = new OnnxEvaluatorOptions.Builder()
                .setExecutionMode(config.transformerExecutionMode().toString())
                .setThreads(config.transformerInterOpThreads(), config.transformerIntraOpThreads());
        if (config.transformerGpuDevice() >= 0)
            optionsBuilder.setGpuDevice(config.transformerGpuDevice());

        var onnxOpts = optionsBuilder.build();
        evaluator = onnx.evaluatorOf(modelHelper.getModelPathResolvingIfNecessary(config.transformerModelReference()), onnxOpts);
        tokenTypeIdsName = detectTokenTypeIds(config, evaluator);
        validateModel();
    }

    private static String detectTokenTypeIds(HuggingFaceEmbedderConfig config, OnnxEvaluator evaluator) {
        String configured = config.transformerTokenTypeIds();
        Map<String, TensorType> inputs = evaluator.getInputInfo();
        if (inputs.size() < 3) {
            // newer models have only 2 inputs (they do not use token type IDs)
            return "";
        } else {
            // could detect fallback from inputs here, currently set as default in .def file
            return configured;
        }
    }

    private void validateModel() {
        Map<String, TensorType> inputs = evaluator.getInputInfo();
        validateName(inputs, inputIdsName, "input");
        validateName(inputs, attentionMaskName, "input");
        if (!tokenTypeIdsName.isEmpty()) {
            validateName(inputs, tokenTypeIdsName, "input");
        }
        Map<String, TensorType> outputs = evaluator.getOutputInfo();
        validateName(outputs, outputName, "output");
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
            Tensor result = poolingStrategy.toSentenceEmbedding(targetType, tokenEmbeddings, embeddingResult.attentionMask);
            return  normalize ? normalize(result, targetType) : result;
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

    Tensor normalize(Tensor embedding, TensorType tensorType) {
        double sumOfSquares = 0.0;

        Tensor.Builder builder = Tensor.Builder.of(tensorType);
        for (int i = 0; i < tensorType.dimensions().get(0).size().get(); i++) {
            double item = embedding.get(TensorAddress.of(i));
            sumOfSquares += item * item;
        }

        double magnitude = Math.sqrt(sumOfSquares);

        for (int i = 0; i < tensorType.dimensions().get(0).size().get(); i++) {
            double value = embedding.get(TensorAddress.of(i));
            builder.cell(value / magnitude, i);
        }

        return builder.build();
    }

    private HuggingFaceEmbedder.HFEmbeddingResult lookupOrEvaluate(Context context, String text) {
        var key = new HFEmbedderCacheKey(context.getEmbedderId(), text);
        return context.computeCachedValueIfAbsent(key, () -> evaluate(context, text));
    }

    private HuggingFaceEmbedder.HFEmbeddingResult evaluate(Context context, String text) {
        var start = System.nanoTime();
        var encoding = tokenizer.encode(text, context.getLanguage());
        runtime.sampleSequenceLength(encoding.ids().size(), context);
        Tensor inputSequence = createTensorRepresentation(encoding.ids(), "d1");
        Tensor attentionMask = createTensorRepresentation(encoding.attentionMask(), "d1");
        Tensor tokenTypeIds = tokenTypeIdsName.isEmpty() ? null : createTensorRepresentation(encoding.typeIds(), "d1");

        Map<String, Tensor> inputs;
        if (tokenTypeIdsName.isEmpty() || tokenTypeIds.isEmpty()) {
            inputs = Map.of(inputIdsName, inputSequence.expand("d0"),
                    attentionMaskName, attentionMask.expand("d0"));
        } else {
            inputs = Map.of(inputIdsName, inputSequence.expand("d0"),
                    attentionMaskName, attentionMask.expand("d0"),
                    tokenTypeIdsName, tokenTypeIds.expand("d0"));
        }
        IndexedTensor tokenEmbeddings = (IndexedTensor) evaluator.evaluate(inputs).get(outputName);
        long[] resultShape = tokenEmbeddings.shape();
        //shape batch, sequence, embedding dimensionality
        if (resultShape.length != 3) {
            throw new IllegalArgumentException("Expected 3 output dimensions for output name '" +
                                               outputName + "': [batch, sequence, embedding], got " + resultShape.length);
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
        Tensor result = poolingStrategy.toSentenceEmbedding(poolingType, embeddingResult.output(), embeddingResult.attentionMask());
        result = normalize? normalize(result, poolingType) : result;
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
