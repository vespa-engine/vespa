package ai.vespa.embedding.huggingface;

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

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Beta
public class HuggingFaceEmbedder extends AbstractComponent implements Embedder {

    private final String inputIdsName;
    private final String attentionMaskName;
    private final String tokenTypeIdsName;
    private final String outputName;
    private final boolean normalize;
    private final HuggingFaceTokenizer tokenizer;
    private final OnnxEvaluator evaluator;

    @Inject
    public HuggingFaceEmbedder(OnnxRuntime onnx, HuggingFaceEmbedderConfig config) {
        inputIdsName = config.transformerInputIds();
        attentionMaskName = config.transformerAttentionMask();
        tokenTypeIdsName = config.transformerTokenTypeIds();
        outputName = config.transformerOutput();
        normalize = config.normalize();
        tokenizer = new HuggingFaceTokenizer.Builder()
                .addSpecialTokens(true)
                .addDefaultModel(Paths.get(config.tokenizerPath().toString()))
                .setTruncation(true)
                .setMaxLength(config.transformerMaxTokens())
                .build();
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
        if (!tokenTypeIdsName.isEmpty()) validateName(inputs, tokenTypeIdsName, "input");

        Map<String, TensorType> outputs = evaluator.getOutputInfo();
        validateName(outputs, outputName, "output");
    }

    private void validateName(Map<String, TensorType> types, String name, String type) {
        if ( ! types.containsKey(name)) {
            throw new IllegalArgumentException("Model does not contain required " + type + ": '" + name + "'. " +
                    "Model contains: " + String.join(",", types.keySet()));
        }
    }

    @Override
    public List<Integer> embed(String s, Context context) {
        return tokenizer.embed(s, context);
    }

    @Override
    public void deconstruct() {
        evaluator.close();
        tokenizer.close();
    }

    @Override
    public Tensor embed(String s, Context context, TensorType tensorType) {
        var encoding = tokenizer.encode(s, context.getLanguage());
        Tensor inputSequence = createTensorRepresentation(encoding.ids(), "d1");
        Tensor attentionMask = createTensorRepresentation(encoding.attentionMask(), "d1");
        Tensor tokenTypeIds = createTensorRepresentation(encoding.typeIds(), "d1");


        Map<String, Tensor> inputs;
        if (tokenTypeIds.isEmpty()) {
            inputs = Map.of(inputIdsName, inputSequence.expand("d0"),
                            attentionMaskName, attentionMask.expand("d0"));
        } else {
            inputs = Map.of(inputIdsName, inputSequence.expand("d0"),
                            attentionMaskName, attentionMask.expand("d0"),
                            tokenTypeIdsName, tokenTypeIds.expand("d0"));
        }

        Map<String, Tensor> outputs = evaluator.evaluate(inputs);
        Tensor tokenEmbeddings = outputs.get(outputName);
        Tensor.Builder builder = Tensor.Builder.of(tensorType);

        // Mean pooling implementation
        Tensor summedEmbeddings = tokenEmbeddings.sum("d1");
        Tensor summedAttentionMask = attentionMask.expand("d0").sum("d1");
        Tensor averaged = summedEmbeddings.join(summedAttentionMask, (x, y) -> x / y);
        for (int i = 0; i < tensorType.dimensions().get(0).size().get(); i++) {
            builder.cell(averaged.get(TensorAddress.of(0,i)), i);
        }

        Tensor result = builder.build();
        return normalize ? normalize(result, tensorType) : result;
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

    private IndexedTensor createTensorRepresentation(List<Long> input, String dimension) {
        int size = input.size();
        TensorType type = new TensorType.Builder(TensorType.Value.FLOAT).indexed(dimension, size).build();
        IndexedTensor.Builder builder = IndexedTensor.Builder.of(type);
        for (int i = 0; i < size; ++i) {
            builder.cell(input.get(i), i);
        }
        return builder.build();
    }


}

