// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.yahoo.tensor.functions.ConstantTensor;
import com.yahoo.tensor.functions.GeneratedTensor;
import com.yahoo.tensor.functions.JoinFunction;
import com.yahoo.tensor.functions.L1Normalize;
import com.yahoo.tensor.functions.L2Normalize;
import com.yahoo.tensor.functions.MapFunction;
import com.yahoo.tensor.functions.ReduceFunction;
import com.yahoo.tensor.functions.RenameFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A multidimensional array which can be used in computations.
 * <p>
 * A tensor consists of a set of <i>dimension</i> names and a set of <i>cells</i> containing scalar <i>values</i>.
 * Each cell is is identified by its <i>address</i>, which consists of a set of dimension-label pairs which defines
 * the location of that cell. Both dimensions and labels are string on the form of an identifier or integer.
 * Any dimension in an address may be assigned the special label "undefined", represented in string form as "-".
 * <p>
 * The size of the set of dimensions of a tensor is called its <i>order</i>.
 * <p>
 * In contrast to regular mathematical formulations of tensors, this definition of a tensor allows <i>sparseness</i>
 * as there is no built-in notion of a contiguous space, and even in cases where a space is implied (such as when
 * address labels are integers), there is no requirement that every implied cell has a defined value.
 * Undefined values have no define representation as they are never observed.
 * <p>
 * Tensors can be read and serialized to and from a string form documented in the {@link #toString} method.
 *
 * @author bratseth
 */
@Beta
public interface Tensor {

    /**
     * Returns the immutable set of dimensions of this tensor.
     * The size of this set is the tensor's <i>order</i>.
     */
    Set<String> dimensions();

    /** Returns an immutable map of the cells of this */
    Map<TensorAddress, Double> cells();

    /** Returns the value of a cell, or NaN if this cell does not exist/have no value */
    double get(TensorAddress address);
    
    // ----------------- Primitive tensor functions
    
    default Tensor map(DoubleUnaryOperator mapper) {
        return new MapFunction(new ConstantTensor(this), mapper).execute();
    }

    /** Aggregates cells over a set of dimensions, or over all dimensions if no dimensions are specified */
    default Tensor reduce(ReduceFunction.Aggregator aggregator, List<String> dimensions) {
        return new ReduceFunction(new ConstantTensor(this), aggregator, dimensions).execute();
    }

    default Tensor join(Tensor argument, DoubleBinaryOperator combinator) {
        return new JoinFunction(new ConstantTensor(this), new ConstantTensor(argument), combinator).execute();
    }
    
    default Tensor rename(List<String> fromDimensions, List<String> toDimensions) {
        return new RenameFunction(new ConstantTensor(this), fromDimensions, toDimensions).execute();
    }
    
    static Tensor from(TensorType type, Function<List<Integer>, Double> valueSupplier) {
        return new GeneratedTensor(type, valueSupplier).execute();
    }
    
    // ----------------- Composite tensor functions which have a defined primitive mapping
    
    default Tensor l1Normalize(String dimension) {
        return new L1Normalize(new ConstantTensor(this), dimension).execute();
    }

    default Tensor l2Normalize(String dimension) {
        return new L2Normalize(new ConstantTensor(this), dimension).execute();
    }

    // ----------------- Composite tensor functions mapped to primitives here on the fly

    default Tensor multiply(Tensor argument) { return join(argument, (a, b) -> (a * b )); }
    default Tensor add(Tensor argument) { return join(argument, (a, b) -> (a + b )); }
    default Tensor divide(Tensor argument) { return join(argument, (a, b) -> (a / b )); }
    default Tensor subtract(Tensor argument) { return join(argument, (a, b) -> (a - b )); }

    default Tensor avg(List<String> dimensions) { return reduce(ReduceFunction.Aggregator.avg, dimensions); }
    default Tensor count(List<String> dimensions) { return reduce(ReduceFunction.Aggregator.count, dimensions); }
    default Tensor max(List<String> dimensions) { return reduce(ReduceFunction.Aggregator.max, dimensions); }
    default Tensor min(List<String> dimensions) { return reduce(ReduceFunction.Aggregator.min, dimensions); }
    default Tensor prod(List<String> dimensions) { return reduce(ReduceFunction.Aggregator.prod, dimensions); }
    default Tensor sum(List<String> dimensions) { return reduce(ReduceFunction.Aggregator.sum, dimensions); }

    // ----------------- Old stuff
    /**
     * Returns the <i>sparse tensor product</i> of this tensor and the argument tensor.
     * This is the all-to-all combinations of cells in the argument tenors, except the combinations
     * which have conflicting labels for the same dimension. The value of each combination is the product
     * of the values of the two input cells. The dimensions of the tensor product is the set union of the
     * dimensions of the argument tensors.
     * <p>
     * If there are no overlapping dimensions this is the regular tensor product.
     * If the two tensors have exactly the same dimensions this is the Hadamard product.
     * <p>
     * The sparse tensor product is associative and commutative.
     *
     * @param argument the tensor to multiply by this
     * @return the resulting tensor.
     */
    default Tensor oldMultiply(Tensor argument) {
        return new TensorProduct(this, argument).result();
    }

    /**
     * Returns the <i>match product</i> of two tensors.
     * This returns a tensor which contains the <i>matching</i> cells in the two tensors, with their
     * values multiplied.
     * <p>
     * Two cells are matching if they have the same labels for all dimensions shared between the two argument tensors,
     * and have the value undefined for any non-shared dimension.
     * <p>
     * The dimensions of the resulting tensor is the set intersection of the two argument tensors.
     * <p>
     * If the two tensors have exactly the same dimensions, this is the Hadamard product.
     */
    default Tensor match(Tensor argument) {
        return new MatchProduct(this, argument).result();
    }

    /**
     * Returns a tensor which contains the cells of both argument tensors, where the value for
     * any <i>matching</i> cell is the min of the two possible values.
     * <p>
     * Two cells are matching if they have the same labels for all dimensions shared between the two argument tensors,
     * and have the value undefined for any non-shared dimension.
     */
    default Tensor min(Tensor argument) {
        return new TensorMin(this, argument).result();
    }

    /**
     * Returns a tensor which contains the cells of both argument tensors, where the value for
     * any <i>matching</i> cell is the max of the two possible values.
     * <p>
     * Two cells are matching if they have the same labels for all dimensions shared between the two argument tensors,
     * and have the value undefined for any non-shared dimension.
     */
    default Tensor max(Tensor argument) {
        return new TensorMax(this, argument).result();
    }

    /**
     * Returns a tensor which contains the cells of both argument tensors, where the value for
     * any <i>matching</i> cell is the sum of the two possible values.
     * <p>
     * Two cells are matching if they have the same labels for all dimensions shared between the two argument tensors,
     * and have the value undefined for any non-shared dimension.
     */
    default Tensor oldAdd(Tensor argument) {
        return new TensorSum(this, argument).result();
    }

    /**
     * Returns a tensor which contains the cells of both argument tensors, where the value for
     * any <i>matching</i> cell is the difference of the two possible values.
     * <p>
     * Two cells are matching if they have the same labels for all dimensions shared between the two argument tensors,
     * and have the value undefined for any non-shared dimension.
     */
    default Tensor oldSubtract(Tensor argument) {
        return new TensorDifference(this, argument).result();
    }

    /**
     * Returns a tensor with the same cells as this and the given function is applied to all its cell values.
     *
     * @param function the function to apply to all cells
     * @return the tensor with the function applied to all the cells of this
     */
    default Tensor apply(UnaryOperator<Double> function) {
        return new TensorFunction(this, function).result();
    }

    /**
     * Returns a tensor with the given dimension removed and cells which contains the sum of the values
     * in the removed dimension.
     */
    default Tensor sum(String dimension) {
        return new TensorDimensionSum(dimension, this).result();
    }

    /**
     * Returns the sum of all the cells of this tensor.
     */
    default double sum() {
        double sum = 0;
        for (Map.Entry<TensorAddress, Double> cell : cells().entrySet())
            sum += cell.getValue();
        return sum;
    }

    /**
     * Returns true if the given tensor is mathematically equal to this:
     * Both are of type Tensor and have the same content.
     */
    @Override
    boolean equals(Object o);

    /** Returns true if the two given tensors are mathematically equivalent, that is whether both have the same content */
    static boolean equals(Tensor a, Tensor b) {
        if (a == b) return true;
        if ( ! a.dimensions().equals(b.dimensions())) return false;
        if ( ! a.cells().equals(b.cells())) return false;
        return true;
    }

    /**
     * Returns this tensor on the form
     * <code>{address1:value1,address2:value2,...}</code>
     * where each address is on the form <code>{dimension1:label1,dimension2:label2,...}</code>,
     * and values are numbers.
     * <p>
     * Cells are listed in the natural order of tensor addresses: Increasing size primarily
     * and by element lexical order secondarily.
     * <p>
     * Note that while this is suggestive of JSON, it is not JSON.
     */
    @Override
    String toString();

    /** Returns a tensor instance containing the given data on the standard string format returned by toString */
    static Tensor from(String tensorString) {
        return MapTensor.from(tensorString);
    }

    /**
     * Returns a tensor instance containing the given data on the standard string format returned by toString
     *
     * @param tensorType the type of the tensor to return, as a string on the tensor type format, given in
     *        {@link TensorType#fromSpec}
     * @param tensorString the tensor on the standard tensor string format
     */
    static Tensor from(String tensorType, String tensorString) {
        TensorType.fromSpec(tensorType); // Just validate type spec for now, as we only have one, generic implementation
        return MapTensor.from(tensorString);
    }

    /**
     * Call this from toString in implementations to return the standard string format.
     * (toString cannot be a default method because default methods cannot override super methods).
     *
     * @param tensor the tensor to return the standard string format of
     * @return the tensor on the standard string format
     */
    static String toStandardString(Tensor tensor) {
        if ( emptyDimensions(tensor).size() > 0) // explicitly output type TODO: Always do that
            return typeToString(tensor) + ":" + contentToString(tensor);
        else
            return contentToString(tensor);
    }

    static String typeToString(Tensor tensor) {
        if (tensor.dimensions().isEmpty()) return "tensor()";
        StringBuilder b = new StringBuilder("tensor(");
        for (String dimension : tensor.dimensions())
            b.append(dimension).append("{},");
        b.setLength(b.length() -1);
        b.append(")");
        return b.toString();
    }
    
    static String contentToString(Tensor tensor) {
        List<Map.Entry<TensorAddress, Double>> cellEntries = new ArrayList<>(tensor.cells().entrySet());
        Collections.sort(cellEntries, Map.Entry.<TensorAddress, Double>comparingByKey());

        StringBuilder b = new StringBuilder("{");
        for (Map.Entry<TensorAddress, Double> cell : cellEntries) {
            b.append(cell.getKey()).append(":").append(cell.getValue());
            b.append(",");
        }
        if (b.length() > 1)
            b.setLength(b.length() - 1);
        b.append("}");
        return b.toString();
    }

    /**
     * Returns the dimensions of this which have no values.
     * This is a possibly empty subset of the dimensions of this tensor.
     */
    static Set<String> emptyDimensions(Tensor tensor) {
        Set<String> emptyDimensions = new HashSet<>(tensor.dimensions());
        for (TensorAddress address : tensor.cells().keySet())
            emptyDimensions.removeAll(address.dimensions());
        return emptyDimensions;
    }

    static String unitTensorWithDimensions(Set<String> dimensions) {
        return new MapTensor(dimensions, ImmutableMap.of()).toString();
    }

}
