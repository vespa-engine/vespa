package com.yahoo.tensor;

import com.yahoo.api.annotations.Beta;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Tensor convenience functions.
 *
 * @author bratseth
 */
@Beta
public class Tensors {

    /**
     * Converts the given dimensions from dense to sparse.
     * Any given dimension which is already dense is left as is.
     *
     * @param tensor the tensor to convert
     * @param dimensions the dimensions to convert from dense to sparse.
     *                   If no dimensions are given, all dimensions are converted.
     * @return a tensor where the specified dimensions are converted from dense to sparse
     * @throws IllegalArgumentException if the given tensor does not have all the specified dimensions
     */
    public static Tensor toSparse(Tensor tensor, String ... dimensions) {
        for (var dimension : dimensions) {
            if (tensor.type().dimension(dimension).isEmpty())
                throw new IllegalArgumentException("The tensor " + tensor.type() + " is missing the specified dimension '" +
                                                   dimension + "'");
        }
        if (dimensions.length == 0)
            dimensions = tensor.type().dimensions().stream().map(TensorType.Dimension::name).toArray(String[]::new);
        var targetTypeBuilder = new TensorType.Builder();
        for (var sourceDimension : tensor.type().dimensions()) {
            if (sourceDimension.isMapped() || Arrays.stream(dimensions).noneMatch(d -> d.equals(sourceDimension.name())))
                targetTypeBuilder.dimension(sourceDimension);
            else
                targetTypeBuilder.mapped(sourceDimension.name());
        }
        var targetType = targetTypeBuilder.build();
        if (tensor.type().equals(targetType)) return tensor;
        var builder = Tensor.Builder.of(targetType);
        for (Iterator<Tensor.Cell> i = tensor.cellIterator(); i.hasNext(); )
            builder.cell(i.next());
        return builder.build();
    }

    /**
     * Converts any tensor containing only ones and zeroes into one where each consecutive 8 values in the
     * dense dimension are packed into a single byte. As a consequence the output type of this is a tensor
     * where the dense dimension is 1/8th as large.
     *
     * @throws IllegalArgumentException if the tensor has the wrong type or contains any other value than 0 or 1
     */
    public static Tensor packBits(Tensor tensor) {
        if (tensor.type().indexedSubtype().dimensions().size() != 1)
            throw new IllegalArgumentException("packBits requires a tensor with one dense dimensions, but got " + tensor.type());

        // Create the packed type
        var typeBuilder = new TensorType.Builder(TensorType.Value.INT8);
        for (var d : tensor.type().dimensions())
            typeBuilder.dimension(d.size().isPresent() ? d.withSize((int) Math.ceil(d.size().get() / 8.0)) : d);
        var packedType = typeBuilder.build();

        // Pack it
        Tensor.Builder builder = Tensor.Builder.of(packedType);
        if (tensor instanceof IndexedTensor indexed) {
            for (long i = 0; i < indexed.size(); ) {
                long packedIndex = i / 8;
                int packedValue = 0;
                for (int j = 0; j < 8 && i < indexed.size(); j++)
                    packedValue = packInto(packedValue, indexed.get(i), j, i++);
                builder.cell(packedValue, packedIndex);
            }
        }
        else if (tensor instanceof MixedTensor mixed) {
            for (var denseSubspace : mixed.getInternalDenseSubspaces()) {
                for (int i = 0; i < denseSubspace.cells.length; ) {
                    var packedAddress = denseSubspace.sparseAddress.fullAddressOf(mixed.type().dimensions(), new int[]{i / 8});
                    int packedValue = 0;
                    for (int j = 0; j < 8 && i < denseSubspace.cells.length; j++)
                        packedValue = packInto(packedValue, denseSubspace.cells[i], j, i++);
                    builder.cell(packedAddress, packedValue);
                }
            }
        }
        else {
            throw new IllegalArgumentException("The argument is neither of type IndexedTensor or MixedTensor, but " +
                                               tensor.getClass());
        }
        return builder.build();
    }

    private static int packInto(int packedValue, double value, int bitPosition, long sourcePosition) {
        if (value == 0.0)
            return packedValue;
        else if (value == 1.0)
            return packedValue | ( 1 << ( 7 - bitPosition ));
        else
            throw new IllegalArgumentException("The tensor to be packed can only contain 0 or 1 values, " +
                                               "but has " + value + " at position " + sourcePosition);
    }

}
