// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import ai.vespa.modelintegration.evaluator.SequencePadder.Slice;
import com.yahoo.tensor.IndexedTensor;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

/**
 * Reads the cells of a padded model output from a buffer into a tensor builder, keeping only those of a
 * {@link Slice}. One method per buffer type, so that the per-cell work is a direct call.
 *
 * @author glebashnik
 */
public final class TensorDecoder {

    private TensorDecoder() {}

    @FunctionalInterface
    public interface Short2Float {
        float convert(short value);
    }

    public static void read(FloatBuffer buffer, Slice slice, IndexedTensor.BoundBuilder builder) {
        int destination = 0;
        for (int row = 0; row < slice.rows(); row++) {
            int source = row * slice.sourceStride();
            for (int i = 0; i < slice.keptPerRow(); i++)
                builder.cellByDirectIndex(destination++, buffer.get(source + i));
        }
    }

    public static void read(DoubleBuffer buffer, Slice slice, IndexedTensor.BoundBuilder builder) {
        int destination = 0;
        for (int row = 0; row < slice.rows(); row++) {
            int source = row * slice.sourceStride();
            for (int i = 0; i < slice.keptPerRow(); i++)
                builder.cellByDirectIndex(destination++, buffer.get(source + i));
        }
    }

    public static void read(ByteBuffer buffer, Slice slice, IndexedTensor.BoundBuilder builder) {
        int destination = 0;
        for (int row = 0; row < slice.rows(); row++) {
            int source = row * slice.sourceStride();
            for (int i = 0; i < slice.keptPerRow(); i++)
                builder.cellByDirectIndex(destination++, buffer.get(source + i));
        }
    }

    public static void read(ShortBuffer buffer, Slice slice, IndexedTensor.BoundBuilder builder) {
        int destination = 0;
        for (int row = 0; row < slice.rows(); row++) {
            int source = row * slice.sourceStride();
            for (int i = 0; i < slice.keptPerRow(); i++)
                builder.cellByDirectIndex(destination++, buffer.get(source + i));
        }
    }

    /** Reads 16-bit floats through the given conversion */
    public static void read(ShortBuffer buffer, Short2Float converter, Slice slice, IndexedTensor.BoundBuilder builder) {
        int destination = 0;
        for (int row = 0; row < slice.rows(); row++) {
            int source = row * slice.sourceStride();
            for (int i = 0; i < slice.keptPerRow(); i++)
                builder.cellByDirectIndex(destination++, converter.convert(buffer.get(source + i)));
        }
    }

    public static void read(IntBuffer buffer, Slice slice, IndexedTensor.BoundBuilder builder) {
        int destination = 0;
        for (int row = 0; row < slice.rows(); row++) {
            int source = row * slice.sourceStride();
            for (int i = 0; i < slice.keptPerRow(); i++)
                builder.cellByDirectIndex(destination++, buffer.get(source + i));
        }
    }

    public static void read(LongBuffer buffer, Slice slice, IndexedTensor.BoundBuilder builder) {
        int destination = 0;
        for (int row = 0; row < slice.rows(); row++) {
            int source = row * slice.sourceStride();
            for (int i = 0; i < slice.keptPerRow(); i++)
                builder.cellByDirectIndex(destination++, buffer.get(source + i));
        }
    }

}
