// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.huggingface.HuggingFaceEmbedder;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.config.ModelReference;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HuggingFaceEmbedderTest {

    static HuggingFaceEmbedder embedder = getEmbedder();
    static HuggingFaceEmbedder normalizedEmbedder = getNormalizedEmbedder();
    static Embedder.Context context = new Embedder.Context("schema.indexing");


    @Test
    public void testEmbedder() {
        String input = "This is a test";
        Tensor result = embedder.embed(input, context, TensorType.fromSpec(("tensor<float>(x[8])")));
        assertEquals("tensor<float>(x[8]):[-0.666825, 0.33570012, 0.22756238, 0.0919357, -0.06958359, 0.32301554, 0.42277765, 0.27041236]", result.toAbbreviatedString());
        // Thresholding on the above gives [0, 1, 1, 1, 0, 1, 1, 1] which is packed as int8 119
        Tensor binarizedResult = embedder.embed(input, context, TensorType.fromSpec(("tensor<int8>(x[1])")));
        assertEquals("tensor<int8>(x[1]):[119]", binarizedResult.toString());

        binarizedResult = embedder.embed(input, context, TensorType.fromSpec(("tensor<int8>(x[2])")));
        assertEquals("tensor<int8>(x[2]):[119, 44]", binarizedResult.toAbbreviatedString());

        binarizedResult = embedder.embed(input, context, TensorType.fromSpec(("tensor<int8>(x[48])")));
        assertTrue(binarizedResult.toAbbreviatedString().startsWith("tensor<int8>(x[48]):[119, 44"));

        assertThrows(IllegalArgumentException.class, () -> {
            // throws because the target tensor type is not compatible with the model output
            //49*8 > 384
            embedder.embed(input, context, TensorType.fromSpec(("tensor<int8>(x[49])")));
        });

        Tensor float16Result = embedder.embed(input, context, TensorType.fromSpec(("tensor<bfloat16>(x[1])")));
        assertEquals("tensor<bfloat16>(x[1]):[-0.666825]", float16Result.toAbbreviatedString());
    }

    @Test
    public void testEmbedderWithNormalization() {
        String input = "This is a test";

        Tensor result = normalizedEmbedder.embed(input, context, TensorType.fromSpec(("tensor<float>(x[8])")));
        assertEquals(1.0, result.multiply(result).sum().asDouble(), 1e-4);

        result = normalizedEmbedder.embed(input, context, TensorType.fromSpec(("tensor<float>(x[16])")));
        assertEquals(1.0,  result.multiply(result).sum().asDouble(), 1e-4);
        Tensor binarizedResult = embedder.embed(input, context, TensorType.fromSpec(("tensor<int8>(x[2])")));
        assertEquals("tensor<int8>(x[2]):[119, 44]", binarizedResult.toAbbreviatedString());
    }

    private static HuggingFaceEmbedder getEmbedder() {
        String vocabPath = "src/test/models/onnx/transformer/real_tokenizer.json";
        String modelPath = "src/test/models/onnx/transformer/embedding_model.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));
        HuggingFaceEmbedderConfig.Builder builder = new HuggingFaceEmbedderConfig.Builder();
        builder.tokenizerPath(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        builder.transformerGpuDevice(-1);
        return new HuggingFaceEmbedder(new OnnxRuntime(), Embedder.Runtime.testInstance(), builder.build());
    }
    private static HuggingFaceEmbedder getNormalizedEmbedder() {
        String vocabPath = "src/test/models/onnx/transformer/real_tokenizer.json";
        String modelPath = "src/test/models/onnx/transformer/embedding_model.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));
        HuggingFaceEmbedderConfig.Builder builder = new HuggingFaceEmbedderConfig.Builder();
        builder.tokenizerPath(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        builder.transformerGpuDevice(-1);
        builder.normalize(true);
        return new HuggingFaceEmbedder(new OnnxRuntime(), Embedder.Runtime.testInstance(), builder.build());
    }
}
