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
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
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

    @Ignore
    public void testPerformanceNotTerrible() {
        String text = "what was the manhattan project in this context it was a secret project to develop a nuclear weapon in world war" +
                " ii the project was led by the united states with the support of the united kingdom and canada";
        long now = System.currentTimeMillis();
        int n = 1000; // 7s on Intel core i9 2.4Ghz (macbook pro, 2019) using custom reduce, 8s if using generic reduce
        for (int i = 0; i < n; i++) {
            assertEmbed("tensor<float>(t{})", text, indexingContext);
        }
        long elapsed = System.currentTimeMillis() - now;
        System.out.println("Elapsed time: " + elapsed + " ms");
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
        // Custom reduce is 14% faster than generic reduce and the default.
        // Keeping as option for performance testing
        spladeEmbedder = getEmbedder(false);
    }
    private static Embedder getEmbedder(boolean useCustomReduce) {
        String vocabPath = "src/test/models/onnx/transformer/real_tokenizer.json";
        String modelPath = "src/test/models/onnx/transformer/dummy_transformer_mlm.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));
        SpladeEmbedderConfig.Builder builder = new SpladeEmbedderConfig.Builder();
        builder.tokenizerPath(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        builder.termScoreThreshold(scoreThreshold);
        builder.transformerGpuDevice(-1);
        return  new SpladeEmbedder(new OnnxRuntime(), Embedder.Runtime.testInstance(), builder.build(), useCustomReduce);
    }
}
