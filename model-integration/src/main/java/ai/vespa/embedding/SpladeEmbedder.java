package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.embedding.SpladeEmbedderConfig;
import com.yahoo.language.huggingface.HuggingFaceTokenizer;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.*;
import com.yahoo.tensor.functions.Reduce;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;


import static com.yahoo.language.huggingface.ModelInfo.TruncationStrategy.LONGEST_FIRST;

/**
 * A SPLADE embedder that is embedding text to a 1-d mapped tensor. For interpretability, the tensor labels
 * are the subword strings from the wordpiece vocabulary that has a score above a threshold (default 0.0). This
 * instead of using the token identifier.
 *
 */
@Beta
public class SpladeEmbedder extends AbstractComponent implements Embedder {
    private final Embedder.Runtime runtime;
    private final String inputIdsName;
    private final String attentionMaskName;
    private final String tokenTypeIdsName;
    private final String outputName;
    private final double termScoreThreshold;
    private final HuggingFaceTokenizer tokenizer;
    private final OnnxEvaluator evaluator;

    @Inject
    public SpladeEmbedder(OnnxRuntime onnx, Embedder.Runtime runtime, SpladeEmbedderConfig config) {
        this.runtime = runtime;
        inputIdsName = config.transformerInputIds();
        attentionMaskName = config.transformerAttentionMask();
        outputName = config.transformerOutput();
        tokenTypeIdsName = config.transformerTokenTypeIds();
        termScoreThreshold = config.termScoreThreshold();

        var tokenizerPath = Paths.get(config.tokenizerPath().toString());
        var builder = new HuggingFaceTokenizer.Builder()
                .addSpecialTokens(true)
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

    /**
     * Validates that the given tensor type is a 1-d mapped tensor.
     *
     * @param target the type to validate
     * @return true if the type is a 1-d mapped tensor
     */
    protected boolean verifyTensorType(TensorType target) {
        return target.dimensions().size() == 1 && target.dimensions().get(0).isMapped();
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
            throw new IllegalArgumentException("Invalid splade embedder tensor destination. " +
                    "Wanted a mapped 1-d tensor, got " + tensorType);
        }
        var start = System.nanoTime();

        var encoding = tokenizer.encode(text, context.getLanguage());
        runtime.sampleSequenceLength(encoding.ids().size(), context);

        Tensor inputSequence = createTensorRepresentation(encoding.ids(), "d1");
        Tensor attentionMask = createTensorRepresentation(encoding.attentionMask(), "d1");
        Tensor tokenTypeIds = createTensorRepresentation(encoding.typeIds(), "d1");

        Map<String, Tensor> inputs = Map.of(inputIdsName, inputSequence.expand("d0"),
                attentionMaskName, attentionMask.expand("d0"),
                tokenTypeIdsName, tokenTypeIds.expand("d0"));

        Map<String, Tensor> outputs = evaluator.evaluate(inputs);
        //Remove batch dim, batch size of 1
        Tensor output = outputs.get(outputName).reduce(Reduce.Aggregator.max, "d0");
        Tensor mappedTensor =  sparsify(output, tensorType);
        runtime.sampleEmbeddingLatency((System.nanoTime() - start)/1_000_000d, context);
        return mappedTensor;
    }

    /**
     * Sparsify the output tensor by applying a threshold on the log of the relu of the output.
     * @param modelOutput the model output tensor of shape d1,dim where d1 is the sequence length and dim is size
     *               of the vocabulary
     * @param tensorType the type of the destination tensor
     * @return A mapped tensor with the terms from the vocab that has a score above the threshold
     */
    public Tensor sparsify(Tensor modelOutput, TensorType tensorType) {
        Tensor logOfRelu = modelOutput.map((x) -> Math.log(1 + Math.max(0, x)));
        Tensor maxReduced = logOfRelu.reduce(Reduce.Aggregator.max, "d1");
        IndexedTensor vocab = (IndexedTensor) maxReduced;
        Tensor.Builder sparseTensor = MappedTensor.Builder.of(tensorType);
        for(int i = 0; i < vocab.size(); i++) {
            var value =  vocab.get(i);
            if (value > termScoreThreshold) {
                String t = tokenizer.decode(List.of((long) i));
                TensorAddress label = TensorAddress.of(List.of(t).toArray(new String[0]));
                sparseTensor.cell(label, value);
            }
        }
        return sparseTensor.build();
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

    @Override
    public void deconstruct() {
        evaluator.close();
        tokenizer.close();
    }
}
