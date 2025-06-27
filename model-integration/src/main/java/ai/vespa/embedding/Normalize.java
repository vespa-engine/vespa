// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

/**
 * A utility class for normalizing embeddings.
 *
 * @author bjorncs
 */
class Normalize {
    private Normalize() {}

    static Tensor normalize(Tensor embedding, TensorType tensorType) {
        double sumOfSquares = 0.0;

        Tensor.Builder builder = Tensor.Builder.of(tensorType);
        for (int i = 0; i < tensorType.dimensions().get(0).size().get(); i++) {
            double item = embedding.get(TensorAddress.of(i));
            sumOfSquares += item * item;
        }

        double magnitude = Math.sqrt(sumOfSquares);

        for (int i = 0; i < tensorType.dimensions().get(0).size().get(); i++) {
            double value = embedding.get(TensorAddress.of(i));
            builder.cell(value / magnitude, i);
        }
        return builder.build();
    }
}
