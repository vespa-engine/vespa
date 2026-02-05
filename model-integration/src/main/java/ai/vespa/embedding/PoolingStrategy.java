// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.embedding;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.Text;
import java.util.Locale;

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
            var builder = Tensor.Builder.of(type);
            for (int i = 0; i < type.dimensions().get(0).size().get(); i++) {
                builder.cell(tokenEmbeddings.get(TensorAddress.of(0,0,i)), i);
            }
            return builder.build();
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

    abstract Tensor toSentenceEmbedding(TensorType type, Tensor tokenEmbeddings, Tensor attentionMask);

    static PoolingStrategy fromString(String strategy) {
        return switch (strategy.toLowerCase(Locale.ROOT)) {
            case "mean" -> MEAN;
            case "none" -> NONE;
            case "cls" -> CLS;
            default -> throw new IllegalArgumentException(Text.format("Unknown pooling strategy '%s'", strategy));
        };
    }
}
