// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The <i>filter_subspaces</i> tensor function selects some cells or subspaces in a mapped or mixed tensor
 *
 * @author arnej
 */
public class FilterSubspaces<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> argument;
    private final DenseSubspaceFunction<NAMETYPE> function;

    private FilterSubspaces(TensorFunction<NAMETYPE> argument, DenseSubspaceFunction<NAMETYPE> function) {
        this.argument = argument;
        this.function = function;
    }

    public FilterSubspaces(TensorFunction<NAMETYPE> argument, String functionArg, TensorFunction<NAMETYPE> function) {
        this(argument, new DenseSubspaceFunction<>(functionArg, function));
        Objects.requireNonNull(argument, "The argument cannot be null");
        Objects.requireNonNull(functionArg, "The functionArg cannot be null");
        Objects.requireNonNull(function, "The function cannot be null");
    }

    private TensorType outputType(TensorType inputType) {
        var m = inputType.mappedSubtype();
        var i = inputType.indexedSubtype();
        var d = function.outputType(i);
        if (m.rank() < 1) {
            throw new IllegalArgumentException(
                    "filter_subspaces needs input with at least 1 mapped dimension, but got: " + inputType);
        }
        return inputType;
    }

    public TensorFunction<NAMETYPE> argument() {
        return argument;
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() {
        return List.of(argument);
    }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if (arguments.size() != 1)
            throw new IllegalArgumentException("FilterSubspaces must have 1 argument, got " + arguments.size());
        return new FilterSubspaces<NAMETYPE>(arguments.get(0), function);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        return new FilterSubspaces<>(argument.toPrimitive(), function.toPrimitive());
    }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) {
        return outputType(argument.type(context));
    }

    record SplitAddr(TensorAddress sparsePart, TensorAddress densePart) {}

    SplitAddr splitAddr(TensorAddress fullAddr, TensorType fullType, TensorType sparseType, TensorType denseType) {
        var mapAddrBuilder = new TensorAddress.Builder(sparseType);
        var idxAddrBuilder = new TensorAddress.Builder(denseType);
        for (int i = 0; i < fullType.dimensions().size(); i++) {
            var dim = fullType.dimensions().get(i);
            if (dim.isMapped()) {
                mapAddrBuilder.add(dim.name(), fullAddr.objectLabel(i));
            } else {
                idxAddrBuilder.add(dim.name(), fullAddr.objectLabel(i));
            }
        }
        var mapAddr = mapAddrBuilder.build();
        var idxAddr = idxAddrBuilder.build();
        return new SplitAddr(mapAddr, idxAddr);
    }

    TensorAddress combineAddr(
            TensorAddress sparsePart,
            TensorAddress densePart,
            TensorType fullType,
            TensorType sparseType,
            TensorType denseType) {
        var addrBuilder = new TensorAddress.Builder(fullType);
        var sparseDims = sparseType.dimensions();
        for (int i = 0; i < sparseDims.size(); i++) {
            var dim = sparseDims.get(i);
            addrBuilder.add(dim.name(), sparsePart.objectLabel(i));
        }
        var denseDims = denseType.dimensions();
        for (int i = 0; i < denseDims.size(); i++) {
            var dim = denseDims.get(i);
            addrBuilder.add(dim.name(), densePart.objectLabel(i));
        }
        return addrBuilder.build();
    }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor input = argument().evaluate(context);
        TensorType fullType = input.type();
        TensorType sparseType = fullType.mappedSubtype();
        TensorType denseType = fullType.indexedSubtype();
        Map<TensorAddress, Tensor.Builder> builders = new HashMap<>();
        for (Iterator<Tensor.Cell> iter = input.cellIterator(); iter.hasNext(); ) {
            var cell = iter.next();
            var split = splitAddr(cell.getKey(), fullType, sparseType, denseType);
            var builder = builders.computeIfAbsent(split.sparsePart(), k -> Tensor.Builder.of(denseType));
            builder.cell(split.densePart(), cell.getValue());
        }
        Tensor.Builder builder = Tensor.Builder.of(fullType);
        for (var entry : builders.entrySet()) {
            TensorAddress mappedAddr = entry.getKey();
            Tensor denseInput = entry.getValue().build();
            Tensor filterResult = function.map(denseInput).sum();
            if (filterResult.asDouble() != 0) {
                for (Iterator<Tensor.Cell> iter = denseInput.cellIterator(); iter.hasNext(); ) {
                    var cell = iter.next();
                    var denseAddr = cell.getKey();
                    var fullAddr = combineAddr(mappedAddr, denseAddr, fullType, sparseType, denseType);
                    builder.cell(fullAddr, cell.getValue());
                }
            }
        }
        return builder.build();
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "filter_subspaces(" + argument.toString(context) + ", " + function + ")";
    }

    @Override
    public int hashCode() {
        return Objects.hash("filter_subspaces", argument, function);
    }
}
