// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.tools;

import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.List;

/**
 * Component internal helpers for embedding
 *
 * @author bratseth
 */
public class Embed {

    /**
     * Convenience function which embeds the given string into the given tensor type (if possible),
     * using the given embedder.
     */
    public static Tensor asTensor(String text,
                                  Embedder embedder,
                                  Embedder.Context context,
                                  TensorType type) {
        if (type.dimensions().size() == 1 && type.dimensions().get(0).isIndexed()) {
            // Build to a list first since we can't reverse a tensor builder
            List<Integer> values = embedder.embed(text, context);

            long maxSize = values.size();
            if (type.dimensions().get(0).size().isPresent())
                maxSize = Math.min(maxSize, type.dimensions().get(0).size().get());

            Tensor.Builder builder = Tensor.Builder.of(type);
            for (int i = 0; i < maxSize; i++)
                builder.cell(values.get(i), i);
            return builder.build();
        }
        else {
            throw new IllegalArgumentException("Don't know how to embed into " + type);
        }
    }

}
