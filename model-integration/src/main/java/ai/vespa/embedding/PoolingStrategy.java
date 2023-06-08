// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.embedding;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

/**
 * @author bjorncs
 */
public enum PoolingStrategy {
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
            var builder = Tensor.Builder.of(type);
            for (int i = 0; i < type.dimensions().get(0).size().get(); i++) {
                builder.cell(tokenEmbeddings.get(TensorAddress.of(0,0,i)), i);
            }
            return builder.build();
        }
    };

    public abstract Tensor toSentenceEmbedding(TensorType type, Tensor tokenEmbeddings, Tensor attentionMask);

    public static PoolingStrategy fromString(String strategy) {
        return switch (strategy.toLowerCase()) {
            case "mean" -> MEAN;
            case "cls" -> CLS;
            default -> throw new IllegalArgumentException("Unknown pooling strategy '%s'".formatted(strategy));
        };
    }
}
