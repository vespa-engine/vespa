// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoolingStrategyTest {

    private static final Tensor TOKEN_EMBEDDINGS = Tensor.from(
            "tensor<float>(d0[1],d1[4],d2[3]):" +
            "[1,2,3, 4,5,6, 7,8,9, 10,11,12]");
    private static final TensorType OUTPUT_TYPE = TensorType.fromSpec("tensor<float>(x[3])");

    @Test
    void lastTokenPoolingSupportsUnpaddedInput() {
        var result = PoolingStrategy.LAST.toSentenceEmbedding(OUTPUT_TYPE, TOKEN_EMBEDDINGS, attentionMask("[1,1,1,1]"));

        assertEquals(Tensor.from("tensor<float>(x[3]):[10,11,12]"), result);
    }

    @Test
    void lastTokenPoolingSupportsRightPadding() {
        var result = PoolingStrategy.LAST.toSentenceEmbedding(OUTPUT_TYPE, TOKEN_EMBEDDINGS, attentionMask("[1,1,0,0]"));

        assertEquals(Tensor.from("tensor<float>(x[3]):[4,5,6]"), result);
    }

    @Test
    void lastTokenPoolingSupportsLeftPadding() {
        var result = PoolingStrategy.LAST.toSentenceEmbedding(OUTPUT_TYPE, TOKEN_EMBEDDINGS, attentionMask("[0,0,1,1]"));

        assertEquals(Tensor.from("tensor<float>(x[3]):[10,11,12]"), result);
    }

    @Test
    void lastTokenPoolingSupportsOnlyFirstTokenAttended() {
        var result = PoolingStrategy.LAST.toSentenceEmbedding(OUTPUT_TYPE, TOKEN_EMBEDDINGS, attentionMask("[1,0,0,0]"));

        assertEquals(Tensor.from("tensor<float>(x[3]):[1,2,3]"), result);
    }

    @Test
    void lastTokenPoolingSupportsTruncatedOutputDimensions() {
        var truncatedType = TensorType.fromSpec("tensor<float>(x[2])");

        var result = PoolingStrategy.LAST.toSentenceEmbedding(truncatedType, TOKEN_EMBEDDINGS, attentionMask("[1,1,1,1]"));

        assertEquals(Tensor.from("tensor<float>(x[2]):[10,11]"), result);
    }

    @Test
    void lastTokenPoolingRejectsEmptyAttentionMask() {
        var exception = assertThrows(IllegalArgumentException.class,
                                     () -> PoolingStrategy.LAST.toSentenceEmbedding(OUTPUT_TYPE, TOKEN_EMBEDDINGS, attentionMask("[0,0,0,0]")));

        assertEquals("Cannot apply last-token pooling to an empty attention mask", exception.getMessage());
    }

    @Test
    void tokenPoolingRejectsTargetDimensionLargerThanModelOutput() {
        var widerType = TensorType.fromSpec("tensor<float>(x[4])");

        for (var strategy : new PoolingStrategy[] { PoolingStrategy.CLS, PoolingStrategy.LAST }) {
            var exception = assertThrows(IllegalArgumentException.class,
                                         () -> strategy.toSentenceEmbedding(widerType, TOKEN_EMBEDDINGS, attentionMask("[1,1,1,1]")));
            assertEquals("Target dimension 4 exceeds the model output dimension 3", exception.getMessage());
        }
    }

    @Test
    void clsPoolingSelectsFirstTokenAndTruncatesDimensions() {
        var result = PoolingStrategy.CLS.toSentenceEmbedding(TensorType.fromSpec("tensor<float>(x[2])"),
                                                             TOKEN_EMBEDDINGS, attentionMask("[1,1,1,1]"));

        assertEquals(Tensor.from("tensor<float>(x[2]):[1,2]"), result);
    }

    @Test
    void parsesLastTokenPoolingStrategy() {
        assertEquals(PoolingStrategy.LAST, PoolingStrategy.fromString("last"));
        assertEquals(PoolingStrategy.LAST, PoolingStrategy.fromString("LAST"));
    }

    /** Builds a mask with the [batch, sequence] shape both embedders feed to the model. */
    private static Tensor attentionMask(String values) {
        return Tensor.from("tensor<float>(d0[1],d1[4]):" + values);
    }

}
