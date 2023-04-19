package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.embedding.BertBaseEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.wordpiece.WordPieceEmbedder;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A BERT Base compatible embedder. This embedder uses a WordPiece embedder to
 * produce a token sequence that is then input to a transformer model. A BERT base
 * compatible transformer model must have three inputs:
 *
 *  - A token sequence (input_ids)
 *  - An attention mask (attention_mask)
 *  - Token types for cross encoding (token_type_ids)
 *
 * See bert-base-embedder.def for configurable parameters.
 *
 * @author lesters
 */
public class BertBaseEmbedder extends AbstractComponent implements Embedder {

    private final int    maxTokens;
    private final int    startSequenceToken;
    private final int    endSequenceToken;
    private final String inputIdsName;
    private final String attentionMaskName;
    private final String tokenTypeIdsName;
    private final String outputName;
    private final String poolingStrategy;

    private final WordPieceEmbedder tokenizer;
    private final OnnxEvaluator evaluator;

    @Inject
    public BertBaseEmbedder(OnnxRuntime onnx, BertBaseEmbedderConfig config) {
        maxTokens = config.transformerMaxTokens();
        startSequenceToken = config.transformerStartSequenceToken();
        endSequenceToken = config.transformerEndSequenceToken();
        inputIdsName = config.transformerInputIds();
        attentionMaskName = config.transformerAttentionMask();
        tokenTypeIdsName = config.transformerTokenTypeIds();
        outputName = config.transformerOutput();
        poolingStrategy = config.poolingStrategy().toString();

        OnnxEvaluatorOptions options = new OnnxEvaluatorOptions();
        options.setExecutionMode(config.onnxExecutionMode().toString());
        options.setInterOpThreads(modifyThreadCount(config.onnxInterOpThreads()));
        options.setIntraOpThreads(modifyThreadCount(config.onnxIntraOpThreads()));

        tokenizer = new WordPieceEmbedder.Builder(config.tokenizerVocab().toString()).build();
        this.evaluator = onnx.evaluatorOf(config.transformerModel().toString(), options);

        validateModel();
    }

    private void validateModel() {
        Map<String, TensorType> inputs = evaluator.getInputInfo();
        validateName(inputs, inputIdsName, "input");
        validateName(inputs, attentionMaskName, "input");
        // some BERT inspired models such as DistilBERT do not have token_type_ids input
        // one can explicitly declare this is such model by setting that config to empty string
        if (!"".equals(tokenTypeIdsName)) {
            validateName(inputs, tokenTypeIdsName, "input");
        }
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
    public List<Integer> embed(String text, Context context) {
        return tokenizer.embed(text, context);
    }

    @Override
    public Tensor embed(String text, Context context, TensorType type) {
        if (type.dimensions().size() != 1) {
            throw new IllegalArgumentException("Error in embedding to type '" + type + "': should only have one dimension.");
        }
        if (!type.dimensions().get(0).isIndexed()) {
            throw new IllegalArgumentException("Error in embedding to type '" + type + "': dimension should be indexed.");
        }
        List<Integer> tokens = embedWithSeparatorTokens(text, context, maxTokens);
        return embedTokens(tokens, type);
    }

    @Override public void deconstruct() { evaluator.close(); }

    Tensor embedTokens(List<Integer> tokens, TensorType type) {
        Tensor inputSequence = createTensorRepresentation(tokens, "d1");
        Tensor attentionMask = createAttentionMask(inputSequence);
        Tensor tokenTypeIds = createTokenTypeIds(inputSequence);


        Map<String, Tensor> inputs;
        if (!"".equals(tokenTypeIdsName)) {
            inputs = Map.of(inputIdsName, inputSequence.expand("d0"),
                                            attentionMaskName, attentionMask.expand("d0"),
                                            tokenTypeIdsName, tokenTypeIds.expand("d0"));
        } else {
            inputs = Map.of(inputIdsName, inputSequence.expand("d0"),
                                 attentionMaskName, attentionMask.expand("d0"));
        }
        Map<String, Tensor> outputs = evaluator.evaluate(inputs);

        Tensor tokenEmbeddings = outputs.get(outputName);

        Tensor.Builder builder = Tensor.Builder.of(type);
        if (poolingStrategy.equals("mean")) {  // average over tokens
            Tensor summedEmbeddings = tokenEmbeddings.sum("d1");
            Tensor summedAttentionMask = attentionMask.expand("d0").sum("d1");
            Tensor averaged = summedEmbeddings.join(summedAttentionMask, (x, y) -> x / y);
            for (int i = 0; i < type.dimensions().get(0).size().get(); i++) {
                builder.cell(averaged.get(TensorAddress.of(0,i)), i);
            }
        } else {  // CLS - use first token
            for (int i = 0; i < type.dimensions().get(0).size().get(); i++) {
                builder.cell(tokenEmbeddings.get(TensorAddress.of(0,0,i)), i);
            }
        }
        return builder.build();
    }

    private List<Integer> embedWithSeparatorTokens(String text, Context context, int maxLength) {
        List<Integer> tokens = new ArrayList<>();
        tokens.add(startSequenceToken);
        tokens.addAll(embed(text, context));
        tokens.add(endSequenceToken);
        if (tokens.size() > maxLength) {
            tokens = tokens.subList(0, maxLength-1);
            tokens.add(endSequenceToken);
        }
        return tokens;
    }

    private IndexedTensor createTensorRepresentation(List<Integer> input, String dimension)  {
        int size = input.size();
        TensorType type = new TensorType.Builder(TensorType.Value.FLOAT).indexed(dimension, size).build();
        IndexedTensor.Builder builder = IndexedTensor.Builder.of(type);
        for (int i = 0; i < size; ++i) {
            builder.cell(input.get(i), i);
        }
        return builder.build();
    }

    private static Tensor createAttentionMask(Tensor d)  {
        return d.map((x) -> x > 0 ? 1:0);
    }

    private static Tensor createTokenTypeIds(Tensor d)  {
        return d.map((x) -> 0);  // Assume only one token type
    }

    private int modifyThreadCount(int numThreads) {
        if (numThreads >= 0)
            return numThreads;
        return Math.max(1, (int) Math.ceil(((double) Runtime.getRuntime().availableProcessors()) / (-1 * numThreads)));
    }

}
