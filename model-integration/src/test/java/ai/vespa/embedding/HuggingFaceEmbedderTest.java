// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;


import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.modelintegration.utils.ModelPathHelper;
import com.yahoo.config.ModelReference;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.UnpackBitsNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.Tensors;
import org.junit.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HuggingFaceEmbedderTest {

    static HuggingFaceEmbedder embedder = getEmbedder();
    static HuggingFaceEmbedder normalizedEmbedder = getNormalizedEmbedder();

    @Test
    public void testBinarization() {
        assertPackRight("tensor(x[8]):[0,0,0,0,0,0,0,0]", "tensor<int8>(x[1]):[0]");
        assertPackRight("tensor(x[8]):[1,1,1,1,1,1,1,1]", "tensor<int8>(x[1]):[-1]");
        assertPackRight("tensor(x[16]):[0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1]", "tensor<int8>(x[2]):[0, -1]");

        assertPackRight("tensor(x[8]):[0,1,0,1,0,1,0,1]", "tensor<int8>(x[1]):[85]");
        assertPackRight("tensor(x[8]):[1,0,1,0,1,0,1,0]", "tensor<int8>(x[1]):[-86]");
        assertPackRight("tensor(x[16]):[0,1,0,1,0,1,0,1,1,0,1,0,1,0,1,0]", "tensor<int8>(x[2]):[85, -86]");

        assertPackRight("tensor(x[8]):[1,1,1,1,0,0,0,0]", "tensor<int8>(x[1]):[-16]");
        assertPackRight("tensor(x[8]):[0,0,0,0,1,1,1,1]", "tensor<int8>(x[1]):[15]");
        assertPackRight("tensor(x[16]):[1,1,1,1,0,0,0,0,0,0,0,0,1,1,1,1]", "tensor<int8>(x[2]):[-16, 15]");
    }

    private void assertPackRight(String input, String expected) {
        Tensor inputTensor = Tensor.from(input);
        Tensor result = Tensors.packBits(inputTensor);
        assertEquals(expected, result.toString());
        // Verify that the unpack_bits ranking feature produce compatible output
        Tensor unpacked = expandBitTensor(result);
        assertEquals(inputTensor.toString(), unpacked.toString());
    }

    @Test
    public void testCaching() {
        var context = new Embedder.Context("schema.indexing");
        var myEmbedderId = "my-hf-embedder";
        context.setEmbedderId(myEmbedderId);

        var input = "This is a test string to embed";
        Tensor result = embedder.embed(input, context,TensorType.fromSpec("tensor<float>(x[8])"));
        HuggingFaceEmbedder.HFEmbedderCacheKey key = new HuggingFaceEmbedder.HFEmbedderCacheKey(myEmbedderId, input);
        var modelOuput = context.getCachedValue(key);
        assertNotNull(modelOuput);

        Tensor binaryResult = embedder.embed(input, context,TensorType.fromSpec("tensor<int8>(x[4])"));
        var modelOuput2 = context.getCachedValue(key);
        assertEquals(modelOuput, modelOuput2);
        assertNotEquals(result, binaryResult);

        var anotherInput = "This is a different test string to embed with the same embedder";
        embedder.embed(anotherInput, context,TensorType.fromSpec("tensor<float>(x[4])"));
        key = new HuggingFaceEmbedder.HFEmbedderCacheKey(myEmbedderId, anotherInput);
        var modelOuput3 = context.getCachedValue(key);
        assertNotEquals(modelOuput, modelOuput3);

        // context cache is shared
        var copyContext = context.copy();
        var anotherEmbedderId = "another-hf-embedder";
        copyContext.setEmbedderId(anotherEmbedderId);
        key = new HuggingFaceEmbedder.HFEmbedderCacheKey(anotherEmbedderId, input);
        assertNull(copyContext.getCachedValue(key));
        embedder.embed(input, copyContext,TensorType.fromSpec("tensor<int8>(x[2])"));
        assertNotEquals(modelOuput, copyContext.getCachedValue(key));
    }
    @Test
    public void testEmbedder() {
        var context = new Embedder.Context("schema.indexing");
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
        var context = new Embedder.Context("schema.indexing");
        Tensor result = normalizedEmbedder.embed(input, context, TensorType.fromSpec(("tensor<float>(x[8])")));
        assertEquals(1.0, result.multiply(result).sum().asDouble(), 1e-3);
        result = normalizedEmbedder.embed(input, context, TensorType.fromSpec(("tensor<float>(x[16])")));
        assertEquals(1.0,  result.multiply(result).sum().asDouble(), 1e-3);
        Tensor binarizedResult = embedder.embed(input, context, TensorType.fromSpec(("tensor<int8>(x[2])")));
        assertEquals("tensor<int8>(x[2]):[119, 44]", binarizedResult.toAbbreviatedString());
    }

    @Test
    public void testThatWrongTensorTypeThrows() {
        var context = new Embedder.Context("schema.indexing");
        String input = "This is a test";
        assertThrows(IllegalArgumentException.class, () -> {
            // throws because the target tensor type is mapped
           embedder.embed(input, context, TensorType.fromSpec(("tensor<float>(x{})")));
        });
        assertThrows(IllegalArgumentException.class, () -> {
            // throws because the target tensor is 0d
            embedder.embed(input, context, TensorType.fromSpec(("tensor<float>(x[0]")));
        });
        assertThrows(IllegalArgumentException.class, () -> {
            // throws because the target tensor is 2d
            embedder.embed(input, context, TensorType.fromSpec(("tensor<float>(x{}, y[2])")));
        });
    }

    @Test
    public void testEmbedderWithNormalizationAndPrefix() {
        String input = "This is a test";
        var context = new Embedder.Context("schema.indexing");
        Tensor result = getNormalizePrefixdEmbedder().embed(input, context, TensorType.fromSpec(("tensor<float>(x[8])")));
        assertEquals(1.0, result.multiply(result).sum().asDouble(), 1e-3);
        result = getNormalizePrefixdEmbedder().embed(input, context, TensorType.fromSpec(("tensor<float>(x[16])")));
        assertEquals(1.0,  result.multiply(result).sum().asDouble(), 1e-3);
        Tensor binarizedResult = getNormalizePrefixdEmbedder().embed(input, context, TensorType.fromSpec(("tensor<int8>(x[2])")));
        assertEquals("tensor<int8>(x[2]):[125, 44]", binarizedResult.toAbbreviatedString());

        var queryContext = new Embedder.Context("query.qt");
        Tensor queryResult = getNormalizePrefixdEmbedder().embed(input, queryContext, TensorType.fromSpec(("tensor<float>(x[8])")));
        assertEquals(1.0, queryResult.multiply(queryResult).sum().asDouble(), 1e-3);
        queryResult = getNormalizePrefixdEmbedder().embed(input, queryContext, TensorType.fromSpec(("tensor<float>(x[16])")));
        assertEquals(1.0,  queryResult.multiply(queryResult).sum().asDouble(), 1e-3);
        Tensor binarizedResultQuery = getNormalizePrefixdEmbedder().embed(input, queryContext, TensorType.fromSpec(("tensor<int8>(x[2])")));
        assertNotEquals(binarizedResult.toAbbreviatedString(), binarizedResultQuery.toAbbreviatedString());
        assertEquals("tensor<int8>(x[2]):[119, -116]", binarizedResultQuery.toAbbreviatedString());
    }

    @Test
    public void testPrepend() {
        var context = new Embedder.Context("schema.indexing");
        String input = "This is a test";
        var embedder = getNormalizePrefixdEmbedder();
        var result = embedder.prependInstruction(input, context);
        assertEquals("This is a document: This is a test",  result);
        var queryContext = new Embedder.Context("query.qt");
        var queryResult = embedder.prependInstruction(input, queryContext);
        assertEquals("Represent this text: This is a test",  queryResult);
    }

    private static HuggingFaceEmbedder getEmbedder() {
        String vocabPath = "src/test/models/onnx/transformer/real_tokenizer.json";
        String modelPath = "src/test/models/onnx/transformer/embedding_model.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));
        HuggingFaceEmbedderConfig.Builder builder = new HuggingFaceEmbedderConfig.Builder();
        builder.tokenizerPath(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        builder.transformerGpuDevice(-1);
        var mockModelPathHelper = new MockModelPathHelper();
        HuggingFaceEmbedder huggingFaceEmbedder = new HuggingFaceEmbedder(OnnxRuntime.testInstance(), Embedder.Runtime.testInstance(), builder.build(), mockModelPathHelper);

        assertTrue(mockModelPathHelper.invokedPaths.containsAll(Set.of(
                "src/test/models/onnx/transformer/real_tokenizer.json",
                "src/test/models/onnx/transformer/embedding_model.onnx"
        )));

        return huggingFaceEmbedder;
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
        var mockModelPathHelper = new MockModelPathHelper();
        HuggingFaceEmbedder huggingFaceEmbedder = new HuggingFaceEmbedder(OnnxRuntime.testInstance(), Embedder.Runtime.testInstance(), builder.build(), mockModelPathHelper);

        assertTrue(mockModelPathHelper.invokedPaths.containsAll(Set.of(
                "src/test/models/onnx/transformer/real_tokenizer.json",
                "src/test/models/onnx/transformer/embedding_model.onnx"
        )));

        return huggingFaceEmbedder;
    }

    private static HuggingFaceEmbedder getNormalizePrefixdEmbedder() {
        String vocabPath = "src/test/models/onnx/transformer/real_tokenizer.json";
        String modelPath = "src/test/models/onnx/transformer/embedding_model.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));
        HuggingFaceEmbedderConfig.Builder builder = new HuggingFaceEmbedderConfig.Builder();
        builder.tokenizerPath(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        builder.transformerGpuDevice(-1);
        builder.normalize(true);
        builder.prependQuery("Represent this text:");
        builder.prependDocument("This is a document:");
        var mockModelPathHelper = new MockModelPathHelper();
        HuggingFaceEmbedder huggingFaceEmbedder = new HuggingFaceEmbedder(OnnxRuntime.testInstance(), Embedder.Runtime.testInstance(), builder.build(), mockModelPathHelper);

        assertTrue(mockModelPathHelper.invokedPaths.containsAll(Set.of(
                "src/test/models/onnx/transformer/real_tokenizer.json",
                "src/test/models/onnx/transformer/embedding_model.onnx"
        )));

        return huggingFaceEmbedder;
    }

    public static Tensor expandBitTensor(Tensor packed) {
        var unpacker = new UnpackBitsNode(new ReferenceNode("input"), TensorType.Value.DOUBLE, "big");
        var context = new MapContext();
        context.put("input", new TensorValue(packed));
        return unpacker.evaluate(context).asTensor();
    }

    static class MockModelPathHelper implements ModelPathHelper {
        Set<String> invokedPaths = new HashSet<>();

        @Override
        public Path getModelPathResolvingIfNecessary(ModelReference modelReference) {
            invokedPaths.add(modelReference.toString());

            return modelReference.value();
        }
    }
}
