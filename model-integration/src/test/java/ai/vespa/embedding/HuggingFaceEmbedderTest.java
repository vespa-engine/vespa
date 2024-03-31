// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.huggingface.HuggingFaceEmbedder;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.config.ModelReference;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TensorAddress;
import org.junit.Test;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.UnpackBitsNode;

public class HuggingFaceEmbedderTest {

    static HuggingFaceEmbedder embedder = getEmbedder();
    static HuggingFaceEmbedder normalizedEmbedder = getNormalizedEmbedder();
    static Embedder.Context context = new Embedder.Context("schema.indexing");

    @Test
    public void testBinarization() {
        TensorType typeOne = TensorType.fromSpec("tensor<int8>(x[1])");
        TensorType typeTwo = TensorType.fromSpec("tensor<int8>(x[2])");
        assertPackRight("tensor(x[8]):[0,0,0,0,0,0,0,0]", "tensor<int8>(x[1]):[0]", typeOne);
        assertPackRight("tensor(x[8]):[1,1,1,1,1,1,1,1]", "tensor<int8>(x[1]):[-1]", typeOne);
        assertPackRight("tensor(x[16]):[0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1]", "tensor<int8>(x[2]):[0, -1]", typeTwo);

        assertPackRight("tensor(x[8]):[0,1,0,1,0,1,0,1]", "tensor<int8>(x[1]):[85]", typeOne);
        assertPackRight("tensor(x[8]):[1,0,1,0,1,0,1,0]", "tensor<int8>(x[1]):[-86]", typeOne);
        assertPackRight("tensor(x[16]):[0,1,0,1,0,1,0,1,1,0,1,0,1,0,1,0]", "tensor<int8>(x[2]):[85, -86]", typeTwo);

        assertPackRight("tensor(x[8]):[1,1,1,1,0,0,0,0]", "tensor<int8>(x[1]):[-16]", typeOne);
        assertPackRight("tensor(x[8]):[0,0,0,0,1,1,1,1]", "tensor<int8>(x[1]):[15]", typeOne);
        assertPackRight("tensor(x[16]):[1,1,1,1,0,0,0,0,0,0,0,0,1,1,1,1]", "tensor<int8>(x[2]):[-16, 15]", typeTwo);
    }

    private void assertPackRight(String input, String expected, TensorType type) {
        Tensor inputTensor = Tensor.from(input);
        Tensor result = HuggingFaceEmbedder.binarize((IndexedTensor) inputTensor, type);
        assertEquals(expected.toString(), result.toString());
        //Verify against what is done in ranking with unpack_bits
        Tensor unpacked = expandBitTensor(result);
        assertEquals(inputTensor.toString(), unpacked.toString());
    }

    @Test
    public void testEmbedder() {
        String input = "This is a test";

        Tensor expected = Tensor.from("tensor<float>(x[8]):[-0.666, 0.335, 0.227, 0.0919, -0.069, 0.323, 0.422, 0.270]");
        Tensor result = embedder.embed(input, context, TensorType.fromSpec(("tensor<float>(x[8])")));
        for(int i = 0; i < 8; i++) {
            assertEquals(expected.get(TensorAddress.of(i)), result.get(TensorAddress.of(i)), 1e-2);
        }
        // Thresholding on the above gives [0, 1, 1, 1, 0, 1, 1, 1] which is packed into 119 (int8)
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
        assertEquals(-0.666, float16Result.sum().asDouble(),1e-3);
    }

    @Test
    public void testEmbedderWithNormalization() {
        String input = "This is a test";

        Tensor result = normalizedEmbedder.embed(input, context, TensorType.fromSpec(("tensor<float>(x[8])")));
        assertEquals(1.0, result.multiply(result).sum().asDouble(), 1e-3);

        result = normalizedEmbedder.embed(input, context, TensorType.fromSpec(("tensor<float>(x[16])")));
        assertEquals(1.0,  result.multiply(result).sum().asDouble(), 1e-3);
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

    public static Tensor expandBitTensor(Tensor packed) {
        var unpacker = new UnpackBitsNode(new ReferenceNode("input"), TensorType.Value.DOUBLE, "big");
        var context = new MapContext();
        context.put("input", new TensorValue(packed));
        return unpacker.evaluate(context).asTensor();
    }
}
