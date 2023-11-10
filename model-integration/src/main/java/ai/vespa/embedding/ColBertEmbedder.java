// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import com.yahoo.api.annotations.Beta;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.embedding.ColBertEmbedderConfig;
import com.yahoo.language.huggingface.HuggingFaceTokenizer;
import com.yahoo.language.process.Embedder;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.UnpackBitsNode;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Reduce;
import java.nio.file.Paths;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.BitSet;
import java.util.Arrays;

import static com.yahoo.language.huggingface.ModelInfo.TruncationStrategy.LONGEST_FIRST;

/**
 * A ColBERT embedder implementation that maps text to multiple vectors, one vector per token subword id.
 * This embedder uses a HuggingFace tokenizer to produce a token sequence that is then input to a transformer model.
 *
 * See col-bert-embedder.def for configurable parameters.
 * @author bergum
 */
@Beta
public class ColBertEmbedder extends AbstractComponent implements Embedder {
    private final Embedder.Runtime runtime;
    private final String inputIdsName;
    private final String attentionMaskName;

    private final String outputName;

    private final HuggingFaceTokenizer tokenizer;
    private final OnnxEvaluator evaluator;

    private final int maxTransformerTokens;
    private final int maxQueryTokens;
    private final int maxDocumentTokens;

    private final long startSequenceToken;
    private final long endSequenceToken;
    private final long maskSequenceToken;


    @Inject
    public ColBertEmbedder(OnnxRuntime onnx, Embedder.Runtime runtime, ColBertEmbedderConfig config) {
        this.runtime = runtime;
        inputIdsName = config.transformerInputIds();
        attentionMaskName = config.transformerAttentionMask();
        outputName = config.transformerOutput();
        maxTransformerTokens = config.transformerMaxTokens();
        maxDocumentTokens = Math.min(config.maxDocumentTokens(), maxTransformerTokens);
        maxQueryTokens = Math.min(config.maxQueryTokens(), maxTransformerTokens);
        startSequenceToken = config.transformerStartSequenceToken();
        endSequenceToken = config.transformerEndSequenceToken();
        maskSequenceToken = config.transformerMaskToken();

        var tokenizerPath = Paths.get(config.tokenizerPath().toString());
        var builder = new HuggingFaceTokenizer.Builder()
                .addSpecialTokens(false)
                .addDefaultModel(tokenizerPath)
                .setPadding(false);
        var info = HuggingFaceTokenizer.getModelInfo(tokenizerPath);
        if (info.maxLength() == -1 || info.truncation() != LONGEST_FIRST) {
            // Force truncation
            // to max length accepted by model if tokenizer.json contains no valid truncation configuration
            int maxLength = info.maxLength() > 0 && info.maxLength() <= config.transformerMaxTokens()
                    ? info.maxLength()
                    : config.transformerMaxTokens();
            builder.setTruncation(true).setMaxLength(maxLength);
        }
        this.tokenizer = builder.build();
        var onnxOpts = new OnnxEvaluatorOptions();

        if (config.transformerGpuDevice() >= 0)
            onnxOpts.setGpuDevice(config.transformerGpuDevice());
        onnxOpts.setExecutionMode(config.transformerExecutionMode().toString());
        onnxOpts.setThreads(config.transformerInterOpThreads(), config.transformerIntraOpThreads());
        evaluator = onnx.evaluatorOf(config.transformerModel().toString(), onnxOpts);
        validateModel();
    }

    public void validateModel() {
        Map<String, TensorType> inputs = evaluator.getInputInfo();
        validateName(inputs, inputIdsName, "input");
        validateName(inputs, attentionMaskName, "input");
        Map<String, TensorType> outputs = evaluator.getOutputInfo();
        validateName(outputs, outputName, "output");
    }

    private void validateName(Map<String, TensorType> types, String name, String type) {
        if (!types.containsKey(name)) {
            throw new IllegalArgumentException("Model does not contain required " + type + ": '" + name + "'. " +
                    "Model contains: " + String.join(",", types.keySet()));
        }
    }

    @Override
    public List<Integer> embed(String text, Context context) {
        throw new UnsupportedOperationException("This embedder only supports embed with tensor type");
    }

    @Override
    public Tensor embed(String text, Context context, TensorType tensorType) {
        if (!verifyTensorType(tensorType)) {
            throw new IllegalArgumentException("Invalid ColBERT embedder tensor destination. " +
                    "Wanted a mixed 2-d mapped-indexed tensor, got " + tensorType);
        }
        if (context.getDestination().startsWith("query")) {
            return embedQuery(text, context, tensorType);
        } else {
            return embedDocument(text, context, tensorType);
        }
    }

    @Override
    public void deconstruct() {
        evaluator.close();
        tokenizer.close();
    }

    protected Tensor embedQuery(String text, Context context, TensorType tensorType) {
        if (tensorType.valueType() == TensorType.Value.INT8)
            throw new IllegalArgumentException("ColBert query embed does not accept int8 tensor value type");

        long Q_TOKEN_ID = 1; // [unused0] token id used during training to differentiate query versus document.

        var start = System.nanoTime();
        var encoding = tokenizer.encode(text, context.getLanguage());
        runtime.sampleSequenceLength(encoding.ids().size(), context);

        List<Long> ids = encoding.ids();
        if (ids.size() > maxQueryTokens - 3)
            ids = ids.subList(0, maxQueryTokens - 3);

        List<Long> inputIds = new ArrayList<>(maxQueryTokens);
        List<Long> attentionMask = new ArrayList<>(maxQueryTokens);

        inputIds.add(startSequenceToken);
        inputIds.add(Q_TOKEN_ID);
        inputIds.addAll(ids);
        inputIds.add(endSequenceToken);

        int length = inputIds.size();

        int padding = maxQueryTokens - length;
        for (int i = 0; i < padding; i++)
            inputIds.add(maskSequenceToken);

        for (int i = 0; i < length; i++)
            attentionMask.add((long) 1);
        for (int i = 0; i < padding; i++)
            attentionMask.add((long) 0);//Do not attend to mask paddings

        Tensor inputIdsTensor = createTensorRepresentation(inputIds, "d1");
        Tensor attentionMaskTensor = createTensorRepresentation(attentionMask, "d1");

        var inputs = Map.of(inputIdsName, inputIdsTensor.expand("d0"),
                attentionMaskName, attentionMaskTensor.expand("d0"));
        Map<String, Tensor> outputs = evaluator.evaluate(inputs);
        Tensor tokenEmbeddings = outputs.get(outputName);
        IndexedTensor result = (IndexedTensor) tokenEmbeddings.reduce(Reduce.Aggregator.min, "d0");

        int dims = tensorType.indexedSubtype().dimensions().get(0).size().get().intValue();
        if (dims != result.shape()[1]) {
            throw new IllegalArgumentException("Token dimensionality does not" +
                    " match indexed dimensionality of " + dims);
        }
        Tensor resultTensor = toFloatTensor(result, tensorType, inputIds.size());
        runtime.sampleEmbeddingLatency((System.nanoTime() - start) / 1_000_000d, context);
        return resultTensor;
    }

    protected Tensor embedDocument(String text, Context context, TensorType tensorType) {
        long D_TOKEN_ID = 2; // [unused1] token id used during training to differentiate query versus document.
        var start = System.nanoTime();
        var encoding = tokenizer.encode(text, context.getLanguage());
        runtime.sampleSequenceLength(encoding.ids().size(), context);

        List<Long> ids = encoding.ids().stream().filter(token
                -> !PUNCTUATION_TOKEN_IDS.contains(token)).toList();

        if (ids.size() > maxDocumentTokens - 3)
            ids = ids.subList(0, maxDocumentTokens - 3);
        List<Long> inputIds = new ArrayList<>(maxDocumentTokens);
        List<Long> attentionMask = new ArrayList<>(maxDocumentTokens);
        inputIds.add(startSequenceToken);
        inputIds.add(D_TOKEN_ID);
        inputIds.addAll(ids);
        inputIds.add(endSequenceToken);
        for (int i = 0; i < inputIds.size(); i++)
            attentionMask.add((long) 1);

        Tensor inputIdsTensor = createTensorRepresentation(inputIds, "d1");
        Tensor attentionMaskTensor = createTensorRepresentation(attentionMask, "d1");

        var inputs = Map.of(inputIdsName, inputIdsTensor.expand("d0"),
                attentionMaskName, attentionMaskTensor.expand("d0"));

        Map<String, Tensor> outputs = evaluator.evaluate(inputs);
        Tensor tokenEmbeddings = outputs.get(outputName);
        IndexedTensor result = (IndexedTensor) tokenEmbeddings.reduce(Reduce.Aggregator.min, "d0");
        Tensor contextualEmbeddings;
        int retainedTokens = inputIds.size() -1; //Do not retain last PAD
        if (tensorType.valueType() == TensorType.Value.INT8) {
            contextualEmbeddings = toBitTensor(result, tensorType, retainedTokens);
        } else {
            contextualEmbeddings = toFloatTensor(result, tensorType, retainedTokens);
        }
        runtime.sampleEmbeddingLatency((System.nanoTime() - start) / 1_000_000d, context);
        return contextualEmbeddings;
    }

    public static Tensor toFloatTensor(IndexedTensor result, TensorType type, int nTokens) {
        int size = type.indexedSubtype().dimensions().size();
        if (size != 1)
            throw new IllegalArgumentException("Indexed tensor must have one dimension");
        int wantedDimensionality = type.indexedSubtype().dimensions().get(0).size().get().intValue();
        int resultDimensionality = (int)result.shape()[1];
        if (resultDimensionality != wantedDimensionality) {
            throw new IllegalArgumentException("Not possible to map token vector embedding with " + resultDimensionality
                    + " + dimensions into tensor with " + wantedDimensionality);
        }
        Tensor.Builder builder = Tensor.Builder.of(type);
        for (int token = 0; token < nTokens; token++) {
            for (int d = 0; d < resultDimensionality; d++) {
                var value = result.get(TensorAddress.of(token, d));
                builder.cell(TensorAddress.of(token,d),value);
            }
        }
        return builder.build();
    }

    public static Tensor toBitTensor(IndexedTensor result, TensorType type, int nTokens) {
        if (type.valueType() != TensorType.Value.INT8)
            throw new IllegalArgumentException("Only a int8 tensor type can be" +
                    " the destination of bit packing");
        int size = type.indexedSubtype().dimensions().size();
        if (size != 1)
            throw new IllegalArgumentException("Indexed tensor must have one dimension");
        int wantedDimensionality = type.indexedSubtype().dimensions().get(0).size().get().intValue();
        int resultDimensionality = (int)result.shape()[1];
        if (resultDimensionality != 8 * wantedDimensionality) {
            throw new IllegalArgumentException("Not possible to pack " + resultDimensionality
                    + " + dimensions into " + wantedDimensionality + " dimensions");
        }
        Tensor.Builder builder = Tensor.Builder.of(type);
        for (int token = 0; token < nTokens; token++) {
            BitSet bitSet = new BitSet(8);
            int key = 0;
            for (int d = 0; d < result.shape()[1]; d++) {
                var value = result.get(TensorAddress.of(token, d));
                int bitIndex = 7 - (d % 8);
                if (value > 0.0) {
                    bitSet.set(bitIndex);
                } else {
                    bitSet.clear(bitIndex);
                }
                if ((d + 1) % 8 == 0) {
                    byte[] bytes = bitSet.toByteArray();
                    byte packed = (bytes.length == 0) ? 0 : bytes[0];
                    builder.cell(TensorAddress.of(token, key), packed);
                    key++;
                    bitSet = new BitSet(8);
                }
            }
        }
        return builder.build();
    }

    public static Tensor expandBitTensor(Tensor packed) {
        var unpacker = new UnpackBitsNode(new ReferenceNode("input"), TensorType.Value.FLOAT, "big");
        var context = new MapContext();
        context.put("input", new TensorValue(packed));
        return unpacker.evaluate(context).asTensor();
    }

    protected boolean verifyTensorType(TensorType target) {
        return target.dimensions().size() == 2 &&
                target.indexedSubtype().rank() == 1 && target.mappedSubtype().rank() == 1;
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

    private static final Set<Long> PUNCTUATION_TOKEN_IDS = new HashSet<>(
            Arrays.asList(999L, 1000L, 1001L, 1002L, 1003L, 1004L, 1005L, 1006L,
                    1007L, 1008L, 1009L, 1010L, 1011L, 1012L, 1013L, 1024L,
                    1025L, 1026L, 1027L, 1028L, 1029L, 1030L, 1031L, 1032L,
                    1033L, 1034L, 1035L, 1036L, 1063L, 1064L, 1065L, 1066L));
}
