package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import com.yahoo.config.UrlReference;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class BertBaseEmbedderTest {

    @Test
    public void testEmbedder() {
        assumeTrue(OnnxEvaluator.isRuntimeAvailable());
        BertBaseEmbedderConfig.Builder builder = new BertBaseEmbedderConfig.Builder();
        builder.tokenizerVocabUrl(new UrlReference("src/test/models/onnx/transformer/dummy_vocab.txt"));
        builder.transformerModelUrl(new UrlReference("src/test/models/onnx/transformer/dummy_transformer.onnx"));
        BertBaseEmbedder embedder = new BertBaseEmbedder(builder.build());

        TensorType destType = TensorType.fromSpec("tensor<float>(x[7])");
        List<Integer> tokens = List.of(1,2,3,4,5);  // use random tokens instead of invoking the tokenizer
        Tensor embedding = embedder.embedTokens(tokens, destType);

        Tensor expected = Tensor.from("tensor<float>(x[7]):[-0.6178509, -0.8135831, 0.34416935, 0.3912577, -0.13068882, 2.5897025E-4, -0.18638384]");
        assertEquals(embedding, expected);
    }

}
