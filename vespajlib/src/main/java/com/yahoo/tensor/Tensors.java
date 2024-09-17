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

}
