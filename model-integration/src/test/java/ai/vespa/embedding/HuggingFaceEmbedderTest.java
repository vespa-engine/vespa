// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.modelintegration.utils.ModelPathHelper;
import com.yahoo.config.ModelReference;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.language.process.Embedder;
import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
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

        // Test byte quantization (1 float per byte, not binary packing)
        Tensor byteQuantizedResult = embedder.embed(input, context, TensorType.fromSpec(("tensor<int8>(x[49])")));
        assertEquals(49, byteQuantizedResult.size());

        assertThrows(IllegalArgumentException.class, () -> {
            // throws because the target tensor dimension exceeds model output dimensions
            // model outputs 384 dimensions, so requesting 385 should fail
            embedder.embed(input, context, TensorType.fromSpec(("tensor<int8>(x[385])")));
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
    public void testByteQuantization() {
        String input = "This is a test";
        var context = new Embedder.Context("schema.indexing");

        Tensor normalizedFloat = normalizedEmbedder.embed(input, context, TensorType.fromSpec("tensor<float>(x[64])"));
        Tensor normalizedInt8 = normalizedEmbedder.embed(input, context, TensorType.fromSpec("tensor<int8>(x[64])"));
        assertEquals(64, normalizedInt8.size());

        // Verify values are in int8 range [-128, 127]
        for (int i = 0; i < 64; i++) {
            double int8Value = normalizedInt8.get(TensorAddress.of(i));
            assertTrue(int8Value >= -128 && int8Value <= 127, "Value " + int8Value + " at index " + i + " in int8 range");

            // Verify quantization is approximately correct (float * 127 ≈ int8)
            double floatValue = normalizedFloat.get(TensorAddress.of(i));
            double expectedInt8 = Math.round(floatValue * 127.0);
            assertEquals(expectedInt8, int8Value, 1.0, "Quantization at index " + i);
        }
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

    @Test
    public void testSentenceEmbedder() {
        String input = "This is a test with lots of input text here";
        var context = new Embedder.Context("schema.indexing");
        Tensor result = getSentenceEmbedder().embed(input, context, TensorType.fromSpec(("tensor<float>(x[3])")));
        assertEquals("tensor<float>(x[3]):[2013.0, 4587.0, 2987.5]", result.toAbbreviatedString());
    }

    private static HuggingFaceEmbedder getSentenceEmbedder() {
        String vocabPath = "src/test/models/onnx/transformer/real_tokenizer.json";
        String modelPath = "src/test/models/onnx/transformer/mock_sentence_embedder.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));
        HuggingFaceEmbedderConfig.Builder builder = new HuggingFaceEmbedderConfig.Builder();
        builder.tokenizerPath(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        builder.transformerOutput("sentence_embedding");
        builder.poolingStrategy(com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig.PoolingStrategy.Enum.none);
        var onnxConfig = new OnnxEvaluatorConfig.Builder().build();
        var mockModelPathHelper = new MockModelPathHelper();
        HuggingFaceEmbedder huggingFaceEmbedder = new HuggingFaceEmbedder(OnnxRuntime.testInstance(), Embedder.Runtime.testInstance(), builder.build(), onnxConfig, mockModelPathHelper);
        assertTrue(mockModelPathHelper.invokedPaths.containsAll(Set.of(
                "src/test/models/onnx/transformer/real_tokenizer.json",
                "src/test/models/onnx/transformer/mock_sentence_embedder.onnx"
        )));
        return huggingFaceEmbedder;
    }

    private static HuggingFaceEmbedder getEmbedder() {
        String vocabPath = "src/test/models/onnx/transformer/real_tokenizer.json";
        String modelPath = "src/test/models/onnx/transformer/embedding_model.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));
        HuggingFaceEmbedderConfig.Builder builder = new HuggingFaceEmbedderConfig.Builder();
        builder.tokenizerPath(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        var onnxConfig = new OnnxEvaluatorConfig.Builder().build();
        var mockModelPathHelper = new MockModelPathHelper();
        HuggingFaceEmbedder huggingFaceEmbedder = new HuggingFaceEmbedder(OnnxRuntime.testInstance(), Embedder.Runtime.testInstance(), builder.build(), onnxConfig, mockModelPathHelper);

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
        builder.normalize(true);
        var onnxConfig = new OnnxEvaluatorConfig.Builder().build();
        var mockModelPathHelper = new MockModelPathHelper();
        HuggingFaceEmbedder huggingFaceEmbedder = new HuggingFaceEmbedder(OnnxRuntime.testInstance(), Embedder.Runtime.testInstance(), builder.build(), onnxConfig,mockModelPathHelper);

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
        builder.normalize(true);
        builder.prependQuery("Represent this text:");
        builder.prependDocument("This is a document:");
        var onnxConfig = new OnnxEvaluatorConfig.Builder().build();
        var mockModelPathHelper = new MockModelPathHelper();
        HuggingFaceEmbedder huggingFaceEmbedder = new HuggingFaceEmbedder(OnnxRuntime.testInstance(), Embedder.Runtime.testInstance(), builder.build(), onnxConfig, mockModelPathHelper);

        assertTrue(mockModelPathHelper.invokedPaths.containsAll(Set.of(
                "src/test/models/onnx/transformer/real_tokenizer.json",
                "src/test/models/onnx/transformer/embedding_model.onnx"
        )));

        return huggingFaceEmbedder;
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
