// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TypeResolver;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
        if (m.rank() != 1) {
            throw new IllegalArgumentException("filter_subspaces needs input with 1 mapped dimension, but got: " + inputType);
        }
        return inputType;
    }

    public TensorFunction<NAMETYPE> argument() { return argument; }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return List.of(argument); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("FilterSubspaces must have 1 argument, got " + arguments.size());
        return new FilterSubspaces<NAMETYPE>(arguments.get(0), function);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        return new FilterSubspaces<>(argument.toPrimitive(), function);
    }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) {
        return outputType(argument.type(context));
    }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor input = argument().evaluate(context);
        TensorType inputType = input.type();
        TensorType inputTypeMapped = inputType.mappedSubtype();
        TensorType inputTypeDense = inputType.indexedSubtype();
        Map<TensorAddress, Tensor.Builder> builders = new HashMap<>();
        for (Iterator<Tensor.Cell> iter = input.cellIterator(); iter.hasNext(); ) {
            var cell = iter.next();
            var fullAddr = cell.getKey();
            var mapAddrBuilder = new TensorAddress.Builder(inputTypeMapped);
            var idxAddrBuilder = new TensorAddress.Builder(inputTypeDense);
            for (int i = 0; i < inputType.dimensions().size(); i++) {
                var dim = inputType.dimensions().get(i);
                if (dim.isMapped()) {
                    mapAddrBuilder.add(dim.name(), fullAddr.objectLabel(i));
                } else {
                    idxAddrBuilder.add(dim.name(), fullAddr.objectLabel(i));
                }
            }
            var mapAddr = mapAddrBuilder.build();
            var builder = builders.computeIfAbsent(mapAddr, k -> Tensor.Builder.of(inputTypeDense));
            var idxAddr = idxAddrBuilder.build();
            builder.cell(idxAddr, cell.getValue());
        }
        TensorType outputType = input.type();
        TensorType denseOutputType = outputType.indexedSubtype();
        var denseOutputDims = denseOutputType.dimensions();
        Tensor.Builder builder = Tensor.Builder.of(outputType);
        for (var entry : builders.entrySet()) {
            TensorAddress mappedAddr = entry.getKey();
            Tensor denseInput = entry.getValue().build();
            Tensor filterResult = function.map(denseInput).sum();
            if (filterResult.asDouble() != 0) {
                for (Iterator<Tensor.Cell> iter = denseInput.cellIterator(); iter.hasNext(); ) {
                    var cell = iter.next();
                    var denseAddr = cell.getKey();
                    var addrBuilder = new TensorAddress.Builder(outputType);
                    for (int i = 0; i < inputTypeMapped.dimensions().size(); i++) {
                        var dim = inputTypeMapped.dimensions().get(i);
                        addrBuilder.add(dim.name(), mappedAddr.objectLabel(i));
                    }
                    for (int i = 0; i < denseOutputDims.size(); i++) {
                        var dim = denseOutputDims.get(i);
                        addrBuilder.add(dim.name(), denseAddr.objectLabel(i));
                    }
                    builder.cell(addrBuilder.build(), cell.getValue());
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
    public int hashCode() { return Objects.hash("filter_subspaces", argument, function); }

}
