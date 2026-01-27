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
class TensorNormalizer {

    private TensorNormalizer() {}

    /** L2 (unit length) normalize the given tensor */
    static Tensor normalize(Tensor tensor) {
        double sumOfSquares = 0.0;

        Tensor.Builder builder = Tensor.Builder.of(tensor.type());
        for (int i = 0; i < tensor.type().dimensions().get(0).size().get(); i++) {
            double item = tensor.get(TensorAddress.of(i));
            sumOfSquares += item * item;
        }

        double magnitude = Math.sqrt(sumOfSquares);

        for (int i = 0; i < tensor.type().dimensions().get(0).size().get(); i++) {
            double value = tensor.get(TensorAddress.of(i));
            builder.cell(value / magnitude, i);
        }
        return builder.build();
    }

}
