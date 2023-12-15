// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.config.ModelReference;
import com.yahoo.embedding.SpladeEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.MappedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class SpladeEmbedderTest {

    static Tensor assertEmbed(String tensorSpec, String text, Embedder.Context context) {
        TensorType destType = TensorType.fromSpec(tensorSpec);
        Tensor result = spladeEmbedder.embed(text, context, destType);
        assertTrue(result instanceof MappedTensor);
        assertTrue(result.toString(true,true).startsWith(tensorSpec));
        return result;
    }

    @Test
    public void testHappyPath() {
        Tensor result = assertEmbed("tensor<float>(t{})", "what was the manhattan project", indexingContext);
        assertEquals(3, result.size()); // only 3 tokens passes the threshold - mock model.
        MappedTensor mappedResult = (MappedTensor) result;

        double value = mappedResult.get(TensorAddress.of(List.of("relief").toArray(new String[0])));
        assertTrue(value > scoreThreshold);
    }

    @Test
    public void testZeroTokens() {
        Tensor result = assertEmbed("tensor<float>(t{})", "", indexingContext);
        assertEquals(0, result.size());
    }

    @Test
    public void throwsOnInvalidTensorType() {
        Throwable exception = assertThrows(RuntimeException.class, () -> {
            assertEmbed("tensor<float>(d[128])", "", indexingContext);
        });
        assertEquals("Invalid splade embedder tensor destination. Wanted a mapped 1-d tensor, got tensor<float>(d[128])",
                exception.getMessage());
    }

    static final Embedder spladeEmbedder;
    static final Embedder.Context indexingContext;
    static final Double scoreThreshold = 1.15;

    static {
        indexingContext = new Embedder.Context("schema.indexing");
        spladeEmbedder = getEmbedder();
    }
    private static Embedder getEmbedder() {
        String vocabPath = "src/test/models/onnx/transformer/real_tokenizer.json";
        String modelPath = "src/test/models/onnx/transformer/dummy_transformer_mlm.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));
        SpladeEmbedderConfig.Builder builder = new SpladeEmbedderConfig.Builder();
        builder.tokenizerPath(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        builder.transformerOutput("logits");
        builder.termScoreThreshold(scoreThreshold);
        builder.transformerGpuDevice(-1);
        return  new SpladeEmbedder(new OnnxRuntime(), Embedder.Runtime.testInstance(), builder.build());
    }
}
