package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.config.ModelReference;
import com.yahoo.embedding.BertBaseEmbedderConfig;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

public class BertBaseEmbedderTest {

    @Test
    public void testEmbedder() {
        String vocabPath = "src/test/models/onnx/transformer/dummy_vocab.txt";
        String modelPath = "src/test/models/onnx/transformer/dummy_transformer.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));

        BertBaseEmbedderConfig.Builder builder = new BertBaseEmbedderConfig.Builder();
        builder.tokenizerVocab(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        BertBaseEmbedder embedder = newBertBaseEmbedder(builder.build());

        TensorType destType = TensorType.fromSpec("tensor<float>(x[7])");
        List<Integer> tokens = List.of(1,2,3,4,5);  // use random tokens instead of invoking the tokenizer
        Tensor embedding = embedder.embedTokens(tokens, destType);

        Tensor expected = Tensor.from("tensor<float>(x[7]):[-0.6178509, -0.8135831, 0.34416935, 0.3912577, -0.13068882, 2.5897025E-4, -0.18638384]");
        assertEquals(embedding, expected);
    }

    @Test
    public void testEmbedderWithoutTokenTypeIdsName() {
        String vocabPath = "src/test/models/onnx/transformer/dummy_vocab.txt";
        String modelPath = "src/test/models/onnx/transformer/dummy_transformer_without_type_ids.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));

        BertBaseEmbedderConfig.Builder builder = new BertBaseEmbedderConfig.Builder();
        builder.tokenizerVocab(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        builder.transformerTokenTypeIds("");
        BertBaseEmbedder embedder = newBertBaseEmbedder(builder.build());

        TensorType destType = TensorType.fromSpec("tensor<float>(x[7])");
        List<Integer> tokens = List.of(1,2,3,4,5);  // use random tokens instead of invoking the tokenizer
        Tensor embedding = embedder.embedTokens(tokens, destType);

        Tensor expected = Tensor.from("tensor<float>(x[7]):[0.10873623, 0.56411576, 0.6044973, -0.4819714, 0.7519982, -0.83261716, 0.30430704]");
        assertEquals(embedding, expected);
    }

    @Test
    public void testEmbedderWithoutTokenTypeIdsNameButWithConfig() {
        String vocabPath = "src/test/models/onnx/transformer/dummy_vocab.txt";
        String modelPath = "src/test/models/onnx/transformer/dummy_transformer_without_type_ids.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));

        BertBaseEmbedderConfig.Builder builder = new BertBaseEmbedderConfig.Builder();
        builder.tokenizerVocab(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        // we did not configured BertBaseEmbedder to accept missing token type ids 
        // so we expect ctor to throw
        assertThrows(IllegalArgumentException.class, () -> { newBertBaseEmbedder(builder.build()); });
    }

    private static BertBaseEmbedder newBertBaseEmbedder(BertBaseEmbedderConfig cfg) {
        return new BertBaseEmbedder(new OnnxRuntime(), cfg);
    }

}
