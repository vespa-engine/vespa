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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

public class ColBertEmbedderTest {

    @Test
    public void testPacking() {
        assertPackedRight(
                "" +
                        "tensor<float>(d1[6],d2[8]):" +
                        "[" +
                        "[0, 0, 0, 0, 0, 0, 0, 1]," +
                        "[0, 0, 0, 0, 0, 1, 0, 1]," +
                        "[0, 0, 0, 0, 0, 0, 1, 1]," +
                        "[0, 1, 1, 1, 1, 1, 1, 1]," +
                        "[1, 0, 0, 0, 0, 0, 0, 0]," +
                        "[1, 1, 1, 1, 1, 1, 1, 1]" +
                        "]",
                    TensorType.fromSpec("tensor<int8>(dt{},x[1])"),
                "tensor<int8>(dt{},x[1]):{0:1.0, 1:5.0, 2:3.0, 3:127.0, 4:-128.0, 5:-1.0}", 6
        );
        assertPackedRight(
                "" +
                        "tensor<float>(d1[2],d2[16]):" +
                        "[" +
                        "[0, 0, 0, 0, 0, 0, 0, 1,   1, 0, 0, 0, 0, 0, 0, 0]," +
                        "[0, 0, 0, 0, 0, 1, 0, 1,   0, 0, 0, 0, 0, 0, 0, 1]" +
                        "]",
                TensorType.fromSpec("tensor<int8>(dt{},x[2])"),
                "tensor<int8>(dt{},x[2]):{0:[1.0, -128.0], 1:[5.0, 1.0]}",2
        );
    }

    @Test
    public void testEmbedder() {
        assertEmbed("tensor<float>(dt{},x[128])", "this is a document", indexingContext);
        assertEmbed("tensor<int8>(dt{},x[16])", "this is a document", indexingContext);
        assertEmbed("tensor<float>(qt{},x[128])", "this is a query", queryContext);

        assertThrows(IllegalArgumentException.class, () -> {
            //throws because int8 is not supported for query context
            assertEmbed("tensor<int8>(qt{},x[16])", "this is a query", queryContext);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            //throws because 16 is less than model output (128) and we want float
            assertEmbed("tensor<float>(qt{},x[16])", "this is a query", queryContext);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            //throws because 128/8 does not fit into 15
            assertEmbed("tensor<int8>(qt{},x[15])", "this is a query", indexingContext);
        });
    }

    @Test
    public void testLenghtLimits() {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < 1024; i++) {
            sb.append("annoyance");
            sb.append(" ");
        }
        String text = sb.toString();
        Tensor fullFloat = assertEmbed("tensor<float>(dt{},x[128])", text, indexingContext);
        assertEquals(511*128,fullFloat.size());

        Tensor query = assertEmbed("tensor<float>(dt{},x[128])", text, queryContext);
        assertEquals(32*128,query.size());

        Tensor binaryRep = assertEmbed("tensor<int8>(dt{},x[16])", text, indexingContext);
        assertEquals(511*16,binaryRep.size());

        Tensor shortDoc = assertEmbed("tensor<int8>(dt{},x[16])", "annoyance", indexingContext);
        // 3 tokens, 16 bytes each = 48 bytes
        //CLS [unused1] sequence
        assertEquals(3*16,shortDoc.size());;
    }

    static Tensor assertEmbed(String tensorSpec, String text, Embedder.Context context) {
        TensorType destType = TensorType.fromSpec(tensorSpec);
        Tensor result = embedder.embed(text, context, destType);
        assertEquals(destType,result.type());
        MixedTensor mixedTensor = (MixedTensor) result;
        if(context == queryContext) {
            assertEquals(32*mixedTensor.denseSubspaceSize(),mixedTensor.size());
        }
        return result;
    }

    static void assertPackedRight(String numbers, TensorType destination, String expected, int size) {
        var in = (IndexedTensor) Tensor.from(numbers);
        Tensor packed = ColBertEmbedder.toBitTensor(in, destination, size);
        assertEquals(expected, packed.toString());
        Tensor unpacked = ColBertEmbedder.expandBitTensor(packed);
        assertEquals(in.shape()[1], unpacked.type().indexedSubtype().dimensions().get(0).size().get().longValue());
        for (int dOuter = 0; dOuter < size; dOuter++) {
            for (int dInner = 0; dInner < in.shape()[1]; dInner++) {
                var addr = TensorAddress.of(dOuter, dInner);
                double oldVal = in.get(addr);
                if (oldVal > 0) {
                    assertEquals(unpacked.get(addr), 1.0, 0.0);
                } else {
                    assertEquals(unpacked.get(addr), 0.0, 0.0);
                }
            }
        }
    }

    static final Embedder embedder;
    static final Embedder.Context indexingContext;
    static final Embedder.Context queryContext;
    static {
        indexingContext = new Embedder.Context("schema.indexing");
        queryContext = new Embedder.Context("query(qt)");
        embedder = getEmbedder();
    }
    private static Embedder getEmbedder() {
        String vocabPath = "src/test/models/onnx/transformer/tokenizer.json";
        String modelPath = "src/test/models/onnx/transformer/colbert-dummy-v2.onnx";
        assumeTrue(OnnxRuntime.isRuntimeAvailable(modelPath));
        ColBertEmbedderConfig.Builder builder = new ColBertEmbedderConfig.Builder();
        builder.tokenizerPath(ModelReference.valueOf(vocabPath));
        builder.transformerModel(ModelReference.valueOf(modelPath));
        builder.transformerGpuDevice(-1);
        return  new ColBertEmbedder(new OnnxRuntime(), Embedder.Runtime.testInstance(), builder.build());
    }
}
