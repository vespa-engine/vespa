// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.config.ModelReference;
import com.yahoo.embedding.ColBertEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.MixedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * @author bergum
 */
public class ColBertEmbedderTest {

    @Test
    public void tesSkipTokens() {
        Set<Long> skipTokens = embedder.getSkipTokens();
        assertTrue(skipTokens.contains(999L));
        assertTrue(skipTokens.contains(1000L));
        assertTrue(skipTokens.contains(1001L));
        assertTrue(skipTokens.contains(1002L));
        assertTrue(skipTokens.contains(1003L));
        assertTrue(skipTokens.contains(1031L));
    }

    @Test
    public void testPacking() {
        assertPackedRight(
                "" +
                        "tensor<float>(d0[1],d1[6],d2[8]):" +
                        "[[" +
                        "[0, 0, 0, 0, 0, 0, 0, 1]," +
                        "[0, 0, 0, 0, 0, 1, 0, 1]," +
                        "[0, 0, 0, 0, 0, 0, 1, 1]," +
                        "[0, 1, 1, 1, 1, 1, 1, 1]," +
                        "[1, 0, 0, 0, 0, 0, 0, 0]," +
                        "[1, 1, 1, 1, 1, 1, 1, 1]" +
                        "]]",
                    TensorType.fromSpec("tensor<int8>(dt{},x[1])"),
                "tensor<int8>(dt{},x[1]):{0:1.0, 1:5.0, 2:3.0, 3:127.0, 4:-128.0, 5:-1.0}", 6
        );
        assertPackedRight(
                "" +
                        "tensor<float>(d0[1],d1[2],d2[16]):" +
                        "[[" +
                        "[0, 0, 0, 0, 0, 0, 0, 1,   1, 0, 0, 0, 0, 0, 0, 0]," +
                        "[0, 0, 0, 0, 0, 1, 0, 1,   0, 0, 0, 0, 0, 0, 0, 1]" +
                        "]]",
                TensorType.fromSpec("tensor<int8>(dt{},x[2])"),
                "tensor<int8>(dt{},x[2]):{0:[1.0, -128.0], 1:[5.0, 1.0]}",2
        );
        assertPackedRight(
                "" +
                        "tensor<float>(d0[1],d1[2],d2[16]):" +
                        "[[" +
                        "[0, 0, 0, 0, 0, 0, 0, 1,   1, 0, 0, 0, 0, 0, 0, 0]," +
                        "[0, 0, 0, 0, 0, 1, 0, 1,   0, 0, 0, 0, 0, 0, 0, 1]" +
                        "]]",
                TensorType.fromSpec("tensor<int8>(dt{},x[1])"),
                "tensor<int8>(dt{},x[1]):{0:1.0, 1:5.0}",2
        );
    }

    @Test
    public void testCachingFloat() {
        int initialEmbeddingsDone = runtime.embeddingsDone;
        var context = new Embedder.Context("schema.indexing");

        var input = "This is a test string to embed";
        var t1 = (MixedTensor) embedder.embed(input, context,TensorType.fromSpec("tensor<float>(dt{},x[8])"));
        assertEquals(initialEmbeddingsDone + 1, runtime.embeddingsDone);

        var t2 = (MixedTensor)embedder.embed(input, context,TensorType.fromSpec("tensor<float>(dt{},x[4])"));
        assertEquals("Cached value was used", initialEmbeddingsDone + 1, runtime.embeddingsDone);

        assertNotEquals(t1,t2);
        for(int token = 0; token < 7; token ++) {
            for(int dim = 0; dim < 4; dim++) { // the four first should be equal
                assertEquals(t1.get(TensorAddress.of(token,dim)),t2.get(TensorAddress.of(token,dim)), 1e-6);
            }
        }
        // t2 only has 4 dimensions so this should be out of bounds which returns 0
        assertEquals(0, t2.get(TensorAddress.of(1,4)), 1e-6);

        input = "This is a different test string to embed";
        embedder.embed(input, context,TensorType.fromSpec("tensor<float>(dt{},x[8])"));
        assertEquals(initialEmbeddingsDone + 2, runtime.embeddingsDone);
    }

    @Test
    public void testCachingInt() {
        int initialEmbeddingsDone = runtime.embeddingsDone;
        var context = new Embedder.Context("schema.indexing");

        var input = "This is a test string to embed";
        var t1 = (MixedTensor) embedder.embed(input, context, TensorType.fromSpec("tensor<int8>(dt{},x[8])"));
        assertEquals(initialEmbeddingsDone + 1, runtime.embeddingsDone);

        var t2 = (MixedTensor)embedder.embed(input, context, TensorType.fromSpec("tensor<int8>(dt{},x[4])"));
        assertEquals("Cached value was used", initialEmbeddingsDone + 1, runtime.embeddingsDone);

        assertNotEquals(t1, t2);
        for(int token = 0; token < 7; token ++) {
            for(int dim = 0; dim < 4; dim++) { // the four first should be equal
                assertEquals(t1.get(TensorAddress.of(token,dim)), t2.get(TensorAddress.of(token,dim)), 1e-6);
            }
        }
        // t2 only has 4 dimensions so this should be out of bounds which returns 0
        assertEquals(0, t2.get(TensorAddress.of(0,4)), 1e-6);

        input = "This is a different test string to embed";
        embedder.embed(input, context,TensorType.fromSpec("tensor<float>(dt{},x[8])"));
        assertEquals(initialEmbeddingsDone + 2, runtime.embeddingsDone);
    }


    @Test
    public void testEmbedder() {
        var indexingContext = new Embedder.Context("schema.indexing");
        assertEmbed("tensor<float>(dt{},x[128])", "this is a document", indexingContext,128);
        assertEmbed("tensor<float>(dt{},x[64])", "this is a document", indexingContext,64);

        assertEmbed("tensor<int8>(dt{},x[16])", "this is a document", indexingContext,16);
        assertEmbed("tensor<int8>(dt{},x[8])", "this is a document", indexingContext,8);
        assertEmbed("tensor<int8>(dt{},x[4])", "this is a document", indexingContext,4);
        assertEmbed("tensor<int8>(dt{},x[3])", "this is a document", indexingContext,3);

        var queryContext = new Embedder.Context("query(qt{})");
        assertEmbed("tensor<float>(qt{},x[128])", "this is a query", queryContext,128);
        assertEmbed("tensor<float>(qt{},x[64])", "this is a query", queryContext,64);

        assertThrows(IllegalArgumentException.class, () -> {
            // throws because int8 is not supported for query context
            assertEmbed("tensor<int8>(qt{},x[16])", "this is a query", queryContext,16);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            // throws because 8*32 is larger than (128)
            assertEmbed("tensor<int8>(qt{},x[32])", "this is a query", queryContext,32);
        });
    }

    @Test
    public void testInputTensorsWordPiece() {
        // wordPiece tokenizer("this is a query !") -> [2023, 2003, 1037, 23032, 999]
        List<Long> tokens = List.of(2023L, 2003L, 1037L, 23032L, 999L);
        ColBertEmbedder.TransformerInput input = embedder.buildTransformerInput(tokens,10,true);
        assertEquals(10,input.inputIds().size());
        assertEquals(10,input.attentionMask().size());
        assertEquals(List.of(101L, 1L, 2023L, 2003L, 1037L, 23032L, 999L, 102L, 103L, 103L),input.inputIds());
        assertEquals(List.of(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 0L, 0L),input.attentionMask());

        input = embedder.buildTransformerInput(tokens,10,false);
        assertEquals(7,input.inputIds().size());
        assertEquals(7,input.attentionMask().size());
        assertEquals(List.of(101L, 2L, 2023L, 2003L, 1037L, 23032L, 102L),input.inputIds());
        assertEquals(List.of(1L, 1L, 1L, 1L, 1L, 1L, 1L),input.attentionMask());
    }

    @Test
    public void testInputTensorsSentencePiece() {
        // Sentencepiece tokenizer("this is a query !") -> [903, 83, 10, 41, 1294, 711]
        // ! is mapped to 711 and is a punctuation character
        List<Long> tokens = List.of(903L, 83L, 10L, 41L, 1294L, 711L);
        ColBertEmbedder.TransformerInput input = multiLingualEmbedder.buildTransformerInput(tokens,10,true);
        assertEquals(10,input.inputIds().size());
        assertEquals(10,input.attentionMask().size());
        assertEquals(List.of(0L, 3L, 903L, 83L, 10L, 41L, 1294L, 711L, 2L, 250001L),input.inputIds());
        assertEquals(List.of(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 0L),input.attentionMask());

        // NO padding for document side and 711 (punctuation) is now filtered out
        input = multiLingualEmbedder.buildTransformerInput(tokens,10,false);
        assertEquals(8,input.inputIds().size());
        assertEquals(8,input.attentionMask().size());
        assertEquals(List.of(0L, 4L, 903L, 83L, 10L, 41L, 1294L, 2L),input.inputIds());
        assertEquals(List.of(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L),input.attentionMask());

        input = multiLingualEmbedder.buildTransformerInput(List.of(711L), 5, true);
        assertEquals(List.of(0L, 3L, 711L,2L, 250001L),input.inputIds());
        assertEquals(List.of(1L, 1L, 1L, 1L, 0L),input.attentionMask());

        input = multiLingualEmbedder.buildTransformerInput(List.of(711L), 5, false);
        assertEquals(List.of(0L, 4L, 2L),input.inputIds());
        assertEquals(List.of(1L, 1L, 1L),input.attentionMask());
    }

    @Test
    public void testLengthLimits() {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < 1024; i++) {
            sb.append("annoyance");
            sb.append(" ");
        }
        String text = sb.toString();
        var indexingContext = new Embedder.Context("schema.indexing");

        Tensor fullFloat = assertEmbed("tensor<float>(dt{},x[128])", text, indexingContext,128);
        assertEquals(512*128,fullFloat.size());

        Tensor binaryRep = assertEmbed("tensor<int8>(dt{},x[16])", text, indexingContext,16);
        assertEquals(512*16,binaryRep.size());

        Tensor shortDoc = assertEmbed("tensor<int8>(dt{},x[16])", "annoyance", indexingContext,16);
        // 4 tokens, 16 bytes each = 64 bytes
        //CLS [unused1] sequence
        assertEquals(4*16,shortDoc.size());;

        var queryContext = new Embedder.Context("query(qt{})");
        Tensor query = assertEmbed("tensor<float>(dt{},x[128])", text, queryContext,128);
        assertEquals(32*128,query.size());

        Tensor shortQuery = assertEmbed("tensor<float>(dt{},x[64])", text, queryContext,64);
        assertEquals(32*64,shortQuery.size());
    }

    @Ignore
    public void testPerf() {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < 256; i++) {
            sb.append("annoyance");
            sb.append(" ");
        }
        String text = sb.toString();
        long now = System.currentTimeMillis();
        int n = 1000;
        for (int i = 0; i < n; i++) {
            assertEmbed("tensor<float>(dt{},x[128])", text, new Embedder.Context("schema.indexing"),128);
        }
        long elapsed = (System.currentTimeMillis() - now);
        System.out.println("Elapsed time: " + elapsed + " ms");
    }

    static Tensor assertEmbed(String tensorSpec, String text, Embedder.Context context, int dimSize) {
        TensorType destType = TensorType.fromSpec(tensorSpec);
        Tensor result = embedder.embed(text, context, destType);
        assertEquals(destType,result.type());
        MixedTensor mixedTensor = (MixedTensor) result;
        assertEquals(dimSize,mixedTensor.denseSubspaceSize());
        if (context.getDestination().startsWith("query")) {
            assertEquals(32*mixedTensor.denseSubspaceSize(),mixedTensor.size());
        }
        return result;
    }

    static void assertPackedRight(String numbers, TensorType destination, String expected, int size) {
        var in = (IndexedTensor) Tensor.from(numbers);
        int targetDim = destination.indexedSubtype().dimensions().get(0).size().get().intValue();
        Tensor packed = ColBertEmbedder.toBitTensor(in, destination, size);
        assertEquals(expected, packed.toString());
        Tensor unpacked = ColBertEmbedder.expandBitTensor(packed);
        for (int dOuter = 0; dOuter < size; dOuter++) {
            for (int dInner = 0; dInner < targetDim*8; dInner++) {
                var addr = TensorAddress.of(dOuter, dInner);
                double oldVal = in.get(TensorAddress.of(0,dOuter, dInner));
                if (oldVal > 0) {
                    assertEquals(unpacked.get(addr), 1.0, 0.0);
                } else {
                    assertEquals(unpacked.get(addr), 0.0, 0.0);
                }
            }
        }
    }

    static final ColBertEmbedder embedder;
    static final ColBertEmbedder multiLingualEmbedder;
    static final CountingRuntime runtime;

    static {
        runtime = new CountingRuntime();
        embedder = createEmbedder(runtime);
        multiLingualEmbedder = getMultiLingualEmbedder(runtime);
    }

    private static ColBertEmbedder createEmbedder(Embedder.Runtime runtime) {
        String vocabPath = "src/test/models/onnx/transformer/real_tokenizer.json";
        String modelPath = "src/test/models/onnx/transformer/colbert-dummy-v2.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));
        ColBertEmbedderConfig.Builder builder = new ColBertEmbedderConfig.Builder();
        builder.tokenizerPath(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        builder.transformerGpuDevice(-1);
        return new ColBertEmbedder(new OnnxRuntime(), runtime, builder.build());
    }

    private static ColBertEmbedder getMultiLingualEmbedder(Embedder.Runtime runtime) {
        String vocabPath = "src/test/models/onnx/transformer/sentence_piece_tokenizer.json";
        String modelPath = "src/test/models/onnx/transformer/colbert-dummy-v2.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));
        ColBertEmbedderConfig.Builder builder = new ColBertEmbedderConfig.Builder();
        builder.tokenizerPath(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        builder.transformerGpuDevice(-1);

        builder.transformerStartSequenceToken(0);
        builder.transformerPadToken(1);
        builder.transformerEndSequenceToken(2);
        builder.transformerMaskToken(250001);
        builder.queryTokenId(3);
        builder.documentTokenId(4);

        return new ColBertEmbedder(new OnnxRuntime(), Embedder.Runtime.testInstance(), builder.build());
    }

    private static class CountingRuntime implements Embedder.Runtime {

        int embeddingsDone = 0;

        @Override
        public void sampleEmbeddingLatency(double millis, Embedder.Context ctx) {
            embeddingsDone++;
        }

        @Override
        public void sampleSequenceLength(long length, Embedder.Context ctx) { }

    }

}
