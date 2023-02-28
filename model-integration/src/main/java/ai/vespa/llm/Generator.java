package ai.vespa.llm;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.sentencepiece.SentencePieceEmbedder;
import com.yahoo.llm.GeneratorConfig;
import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.PartialAddress;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
* A text generator based on language models (LLMs). By configuring a
 * sentencepience tokenizer and models for encoding and decoding, this
 * component generates text based on the given prompt.
 *
 * See llm.generator.def for configurable parameters.
 *
 * @author lesters
 */
public class Generator extends AbstractComponent {

    private final static int TOKEN_EOS = 1;  // end of sequence

    private final static String BATCH_DIMENSION = "d0";
    private final static String SEQUENCE_DIMENSION = "d1";

    private final int tokenizerMaxTokens;
    private final String encoderInputIdsName;
    private final String encoderAttentionMaskName;
    private final String encoderOutputName;
    private final String decoderInputIdsName;
    private final String decoderAttentionMaskName;
    private final String decoderEncoderHiddenStateName;
    private final String decoderOutputName;

    private final SentencePieceEmbedder tokenizer;
    private final OnnxEvaluator encoder;
    private final OnnxEvaluator decoder;

    @Inject
    public Generator(OnnxRuntime onnx, GeneratorConfig config) {
        // Set up tokenizer
        tokenizer = new SentencePieceEmbedder.Builder(config.tokenizerModel().toString()).build();
        tokenizerMaxTokens = config.tokenizerMaxTokens();

        // Set up encoder
        encoderInputIdsName = config.encoderModelInputIdsName();
        encoderAttentionMaskName = config.encoderModelAttentionMaskName();
        encoderOutputName = config.encoderModelOutputName();

        OnnxEvaluatorOptions encoderOptions = new OnnxEvaluatorOptions();
        encoderOptions.setExecutionMode(config.encoderOnnxExecutionMode().toString());
        encoderOptions.setInterOpThreads(modifyThreadCount(config.encoderOnnxInterOpThreads()));
        encoderOptions.setIntraOpThreads(modifyThreadCount(config.encoderOnnxIntraOpThreads()));

        encoder = onnx.evaluatorOf(config.encoderModel().toString(), encoderOptions);

        // Set up decoder
        decoderInputIdsName = config.decoderModelInputIdsName();
        decoderAttentionMaskName = config.decoderModelAttentionMaskName();
        decoderEncoderHiddenStateName = config.decoderModelEncoderHiddenStateName();
        decoderOutputName = config.decoderModelOutputName();

        OnnxEvaluatorOptions decoderOptions = new OnnxEvaluatorOptions();
        decoderOptions.setExecutionMode(config.decoderOnnxExecutionMode().toString());
        decoderOptions.setInterOpThreads(modifyThreadCount(config.decoderOnnxInterOpThreads()));
        decoderOptions.setIntraOpThreads(modifyThreadCount(config.decoderOnnxIntraOpThreads()));

        decoder = onnx.evaluatorOf(config.decoderModel().toString(), decoderOptions);

        validateModels();
    }

    /**
     * Generates text by evaluating an encoder model to encode the prompt, and
     * repeatedly evaluating a decoding model to generate tokens until some
     * stopping criteria has been met.
     *
     * @param prompt the prompt to generate text from
     * @param options options for text generation
     * @return a text generated from the prompt
     */
    public String generate(String prompt, GeneratorOptions options) {
        return switch (options.getSearchMethod()) {
            case GREEDY -> generateGreedy(prompt, options);
            default -> generateNotImplemented(options);
        };
    }

    public String generate(String prompt) {
        return generate(prompt, new GeneratorOptions());
    }

    @Override public void deconstruct() { encoder.close(); decoder.close(); }

    private String generateNotImplemented(GeneratorOptions options) {
        throw new UnsupportedOperationException("Search method '" + options.getSearchMethod() + "' is currently not implemented");
    }

    private String generateGreedy(String prompt, GeneratorOptions options) {
        var generatedTokens = new ArrayList<Integer>();
        generatedTokens.add(0);  // Or target tokens

        // Tokenize
        var inputTokens = tokenize(prompt);  // Or source tokens

        // Evaluate encoder
        var encoderInput  = createTensorRepresentation(inputTokens, SEQUENCE_DIMENSION);
        var encoderMask   = createAttentionMask(encoderInput).expand(BATCH_DIMENSION);
        var encoderOutput = evaluateEncoder(encoderInput.expand(BATCH_DIMENSION), encoderMask);

        // Greedy search just grabs the next most probable token
        while (generatedTokens.size() < options.getMaxLength()) {  // Todo: add stopping criteria
            var decoderInput = createTensorRepresentation(generatedTokens, SEQUENCE_DIMENSION).expand(BATCH_DIMENSION);
            var logits       = evaluateDecoder(decoderInput, encoderMask, encoderOutput);
            var nextToken    = findMostProbableToken(logits, generatedTokens.size()-1, BATCH_DIMENSION, SEQUENCE_DIMENSION);
            generatedTokens.add(nextToken);
        }

        return detokenize(generatedTokens);
    }

    private Tensor evaluateEncoder(Tensor input, Tensor mask) {
        var encoderInputs = Map.of(encoderInputIdsName, input,
                                   encoderAttentionMaskName, mask);
        return encoder.evaluate(encoderInputs, encoderOutputName);
    }

    private IndexedTensor evaluateDecoder(Tensor input, Tensor encoderMask, Tensor encoderOutput) {
        var inputs = Map.of(decoderInputIdsName, input,
                            decoderAttentionMaskName, encoderMask,  // yes, encoder's attention mask
                            decoderEncoderHiddenStateName, encoderOutput);
        var output  = decoder.evaluate(inputs, decoderOutputName);
        if ( ! (output instanceof IndexedTensor indexedTensor)) {
            throw new IllegalArgumentException("Output of decoder model is not an 'IndexedTensor'");
        }
        return indexedTensor;
    }

    /**
     * Given a tensor 'logits' with 3 dimensions: batch, sequence, and vocabulary
     * find the value in the vocabulary dimension with highest score for the given
     * token in the sequence
     */
    private static int findMostProbableToken(IndexedTensor logits, int seqIndex, String batchDim, String seqDim) {
        if (logits.type().rank() != 3) {
            throw new IllegalArgumentException("Expected a tensor with rank 3: batch, sequence, and vocabulary size. " +
                                               "Got: " + logits.type());
        }
        var iterator = logits.cellIterator(new PartialAddress.Builder(2).
                                                add(batchDim, 0).
                                                add(seqDim, seqIndex).build(),
                                           DimensionSizes.of(logits.type()));
        var maxVal = iterator.next().getValue();
        int maxIndex = 0;
        for (int i = 1; iterator.hasNext(); ++i) {
            var val = iterator.next().getValue();
            if (val >= maxVal && i != TOKEN_EOS) {
                maxVal = val;
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    private List<Integer> tokenize(String text) {
        var tokens = tokenizer.embed(text, new Embedder.Context("tokenizer"));
        tokens = tokens.size() >= tokenizerMaxTokens ? tokens.subList(0,tokenizerMaxTokens-1): tokens;
        tokens.add(TOKEN_EOS);
        return tokens;
    }

    private String detokenize(List<Integer> tokens) {
        return tokenizer.decode(tokens, new Embedder.Context("tokenizer"), true);
    }

    private static Tensor createTensorRepresentation(List<Integer> tokens, String dimension) {
        var size = tokens.size();
        TensorType type = new TensorType.Builder(TensorType.Value.FLOAT).indexed(dimension, size).build();
        IndexedTensor.Builder builder = IndexedTensor.Builder.of(type);
        for (int i = 0; i < size; ++i) {
            builder.cell(tokens.get(i), i);
        }
        return builder.build();
    }

    private static Tensor createAttentionMask(Tensor d)  {
        return d.map((x) -> x > 0 ? 1:0);
    }

    private void validateModels() {
        Map<String, TensorType> inputs = encoder.getInputInfo();
        validateName(inputs, encoderInputIdsName, "input");
        validateName(inputs, encoderAttentionMaskName, "input");

        Map<String, TensorType> outputs = encoder.getOutputInfo();
        validateName(outputs, encoderOutputName, "output");

        inputs = decoder.getInputInfo();
        validateName(inputs, decoderInputIdsName, "input");
        validateName(inputs, decoderAttentionMaskName, "input");
        validateName(inputs, decoderEncoderHiddenStateName, "input");

        outputs = decoder.getOutputInfo();
        validateName(outputs, decoderOutputName, "output");
    }

    private void validateName(Map<String, TensorType> types, String name, String type) {
        if ( ! types.containsKey(name)) {
            throw new IllegalArgumentException("Model does not contain required " + type + ": '" + name + "'. " +
                    "Model contains: " + String.join(",", types.keySet()));
        }
    }

    private int modifyThreadCount(int numThreads) {
        if (numThreads >= 0)
            return numThreads;
        return Math.max(1, (int) Math.ceil(((double) Runtime.getRuntime().availableProcessors()) / (-1 * numThreads)));
    }
}
