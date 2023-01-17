package ai.vespa.embedding.huggingface;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class HuggingFaceEmbedder implements Embedder {

    private static final Logger LOG = LoggerFactory.getLogger(HuggingFaceEmbedder.class.getName());

    private final String inputIdsName;
    private final String attentionMaskName;
    private final String outputName;
    private final int maxTokens;
    private final HuggingFaceTokenizer tokenizer;
    private final OnnxEvaluator evaluator;

    @Inject
    public HuggingFaceEmbedder(HuggingFaceEmbedderConfig config) throws IOException {
        maxTokens = config.transformerMaxTokens();
        inputIdsName = config.transformerInputIds();
        attentionMaskName = config.transformerAttentionMask();
        outputName = config.transformerOutput();

        try {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                tokenizer = HuggingFaceTokenizer.newInstance(Paths.get(config.tokenizerPath().toString()));
            } finally {
                Thread.currentThread().setContextClassLoader(tccl);
            }
        } catch (IOException e){
            LOG.info("Could not initialize the tokenizer");
	    throw new IOException("Could not initialize the tokenizer.");
        }
        evaluator = new OnnxEvaluator(config.transformerModel().toString());
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
        if ( ! types.containsKey(name)) {
            throw new IllegalArgumentException("Model does not contain required " + type + ": '" + name + "'. " +
                    "Model contains: " + String.join(",", types.keySet()));
        }
    }

    @Override
    public List<Integer> embed(String s, Context context) {
        Encoding encoding = tokenizer.encode(s);
        List<Integer> tokenIds = longToInteger(encoding.getIds());

        int tokensSize = tokenIds.size();

        if (tokensSize > maxTokens) {
            Integer lastElement = tokenIds.get(tokensSize - 1);
            tokenIds = tokenIds.subList(0, maxTokens - 1);
            tokenIds.add(lastElement);
        }
        return tokenIds;
    }

    public List<Integer> longToInteger(long[] values) {
        return Arrays.stream(values)
                .boxed().map(Long::intValue)
                .toList();
    }

    @Override
    public Tensor embed(String s, Context context, TensorType tensorType) {
        List<Integer> tokenIds = embed(s.toLowerCase(), context);
        return embedTokens(tokenIds, tensorType);
    }

    Tensor embedTokens(List<Integer> tokenIds, TensorType tensorType) {
        Tensor inputSequence = createTensorRepresentation(tokenIds, "d1");
        Tensor attentionMask = createAttentionMask(inputSequence);

        Map<String, Tensor> inputs = Map.of(
                inputIdsName, inputSequence.expand("d0"),
                attentionMaskName, attentionMask.expand("d0")
        );

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

        return normalize(builder.build(), tensorType);
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

    private IndexedTensor createTensorRepresentation(List<Integer> input, String dimension) {
        int size = input.size();
        TensorType type = new TensorType.Builder(TensorType.Value.FLOAT).indexed(dimension, size).build();
        IndexedTensor.Builder builder = IndexedTensor.Builder.of(type);
        for (int i = 0; i < size; ++i) {
            builder.cell(input.get(i), i);
        }
        return builder.build();
    }

    private Tensor createAttentionMask(Tensor inputSequence) {
        return inputSequence.map((x) -> 1);
    }

}

