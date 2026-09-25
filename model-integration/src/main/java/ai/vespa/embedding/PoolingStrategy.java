// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.embedding;

import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.Text;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * @author bjorncs
 */
enum PoolingStrategy {
    MEAN {
        @Override
        public Tensor toSentenceEmbedding(TensorType type, Tensor tokenEmbeddings, Tensor attentionMask) {
            var builder = Tensor.Builder.of(type);
            var summedEmbeddings = tokenEmbeddings.sum("d1");
            var summedAttentionMask = attentionMask.expand("d0").sum("d1");
            var averaged = summedEmbeddings.join(summedAttentionMask, (x, y) -> x / y);
            for (int i = 0; i < type.dimensions().get(0).size().get(); i++) {
                builder.cell(averaged.get(TensorAddress.of(0, i)), i);
            }
            return builder.build();
        }
    },
    CLS {
        @Override
        public Tensor toSentenceEmbedding(TensorType type, Tensor tokenEmbeddings, Tensor ignored) {
            return tokenEmbedding(type, tokenEmbeddings, 0);
        }
    },
    LAST {
        @Override
        public Tensor toSentenceEmbedding(TensorType type, Tensor tokenEmbeddings, Tensor attentionMask) {
            var mask = (IndexedTensor) attentionMask;
            long sequenceLength = mask.shape()[1];
            for (long token = sequenceLength - 1; token >= 0; token--) {
                if (mask.get(0, token) != 0) {
                    return tokenEmbedding(type, tokenEmbeddings, token);
                }
            }
            throw new IllegalArgumentException("Cannot apply last-token pooling to an empty attention mask");
        }
    },
    NONE {
        @Override
        public Tensor toSentenceEmbedding(TensorType type, Tensor tokenEmbeddings, Tensor ignored) {
            var builder = Tensor.Builder.of(type);
            for (int i = 0; i < type.dimensions().get(0).size().get(); i++) {
                builder.cell(tokenEmbeddings.get(TensorAddress.of(0,i)), i);
            }
            return builder.build();
        }
    };

    /**
     * Pools the token embeddings of one sequence into a sentence embedding.
     *
     * @param type            the type of the sentence embedding to produce
     * @param tokenEmbeddings the model output, indexed as [batch, sequence, embedding]
     * @param attentionMask   the attention mask fed to the model, indexed as [batch, sequence]
     */
    abstract Tensor toSentenceEmbedding(TensorType type, Tensor tokenEmbeddings, Tensor attentionMask);

    /**
     * Copies the embedding of one token into a tensor of the target type.
     * Model outputs have only indexed dimensions, so the evaluators always produce an IndexedTensor.
     */
    private static Tensor tokenEmbedding(TensorType type, Tensor tokenEmbeddings, long token) {
        var embeddings = (IndexedTensor) tokenEmbeddings;
        long targetDimensions = type.dimensions().get(0).size().orElseThrow();
        long outputDimensions = embeddings.shape()[2];
        if (targetDimensions > outputDimensions) {
            throw new IllegalArgumentException("Target dimension " + targetDimensions +
                                               " exceeds the model output dimension " + outputDimensions);
        }
        var builder = Tensor.Builder.of(type);
        for (int i = 0; i < targetDimensions; i++) {
            builder.cell(embeddings.get(0, token, i), i);
        }
        return builder.build();
    }

    static PoolingStrategy fromString(String strategy) {
        return switch (toLowerCase(strategy)) {
            case "mean" -> MEAN;
            case "none" -> NONE;
            case "cls" -> CLS;
            case "last" -> LAST;
            default -> throw new IllegalArgumentException(Text.format("Unknown pooling strategy '%s'", strategy));
        };
    }
}
