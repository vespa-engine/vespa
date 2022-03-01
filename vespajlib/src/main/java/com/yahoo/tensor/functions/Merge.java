// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.PartialAddress;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TypeResolver;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;

/**
 * The <i>merge</i> tensor operation produces from two argument tensors having equal types
 * a tensor having the same type where the values are the union of the values of both tensors. In the cases where both
 * tensors contain a value for a given cell, and only then, the lambda scalar expression is evaluated to produce
 * the resulting cell value.
 *
 * @author bratseth
 */
public class Merge<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> argumentA, argumentB;
    private final DoubleBinaryOperator merger;

    public Merge(TensorFunction<NAMETYPE> argumentA, TensorFunction<NAMETYPE> argumentB, DoubleBinaryOperator merger) {
        Objects.requireNonNull(argumentA, "The first argument tensor cannot be null");
        Objects.requireNonNull(argumentB, "The second argument tensor cannot be null");
        Objects.requireNonNull(merger, "The merger function cannot be null");
        this.argumentA = argumentA;
        this.argumentB = argumentB;
        this.merger = merger;
    }

    /** Returns the type resulting from applying Merge to the two given types */
    public static TensorType outputType(TensorType a, TensorType b) {
        return TypeResolver.merge(a, b);
    }

    public DoubleBinaryOperator merger() { return merger; }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return ImmutableList.of(argumentA, argumentB); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 2)
            throw new IllegalArgumentException("Merge must have 2 arguments, got " + arguments.size());
        return new Merge<>(arguments.get(0), arguments.get(1), merger);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        return new Merge<>(argumentA.toPrimitive(), argumentB.toPrimitive(), merger);
    }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) {
        return outputType(argumentA.type(context), argumentB.type(context));
    }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor a = argumentA.evaluate(context);
        Tensor b = argumentB.evaluate(context);
        TensorType mergedType = outputType(a.type(), b.type());
        return evaluate(a, b, mergedType, merger);
    }


    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "merge(" + argumentA.toString(context) + ", " + argumentB.toString(context) + ", " + merger + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("merge", argumentA, argumentB, merger); }

    static Tensor evaluate(Tensor a, Tensor b, TensorType mergedType, DoubleBinaryOperator combinator) {
        // Choose merge algorithm
        if (hasSingleIndexedDimension(a) && hasSingleIndexedDimension(b) && a.type().dimensions().get(0).name().equals(b.type().dimensions().get(0).name()))
            return indexedVectorMerge((IndexedTensor)a, (IndexedTensor)b, mergedType, combinator);
        else
            return generalMerge(a, b, mergedType, combinator);
    }

    private static boolean hasSingleIndexedDimension(Tensor tensor) {
        return tensor.type().dimensions().size() == 1 && tensor.type().dimensions().get(0).isIndexed();
    }

    private static Tensor indexedVectorMerge(IndexedTensor a, IndexedTensor b, TensorType type, DoubleBinaryOperator combinator) {
        long aSize = a.dimensionSizes().size(0);
        long bSize = b.dimensionSizes().size(0);
        long mergedSize = Math.max(aSize, bSize);
        long sharedSize = Math.min(aSize, bSize);
        Iterator<Double> aIterator = a.valueIterator();
        Iterator<Double> bIterator = b.valueIterator();
        IndexedTensor.Builder builder = IndexedTensor.Builder.of(type);
        for (long i = 0; i < sharedSize; i++)
            builder.cell(combinator.applyAsDouble(aIterator.next(), bIterator.next()), i);
        Iterator<Double> largestIterator = aSize > bSize ? aIterator : bIterator;
        for (long i = sharedSize; i < mergedSize; i++)
            builder.cell(largestIterator.next(), i);
        return builder.build();
    }

    private static Tensor generalMerge(Tensor a, Tensor b, TensorType mergedType, DoubleBinaryOperator combinator) {
        Tensor.Builder builder = Tensor.Builder.of(mergedType);
        addCellsOf(a, b, builder, combinator);
        addCellsOf(b, a, builder, null);
        return builder.build();
    }

    private static void addCellsOf(Tensor a, Tensor b, Tensor.Builder builder, DoubleBinaryOperator combinator) {
        for (Iterator<Tensor.Cell> i = a.cellIterator(); i.hasNext(); ) {
            Map.Entry<TensorAddress, Double> aCell = i.next();
            var key = aCell.getKey();
            if (! b.has(key)) {
                builder.cell(key, aCell.getValue());
            } else if (combinator != null) {
                builder.cell(key, combinator.applyAsDouble(aCell.getValue(), b.get(key)));
            }
        }
    }

}

