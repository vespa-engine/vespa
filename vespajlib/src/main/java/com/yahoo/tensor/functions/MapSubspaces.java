// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TypeResolver;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The <i>map_subspaces</i> tensor function transforms each dense subspace in a (mixed) tensor
 *
 * @author arnej
 */
public class MapSubspaces<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> argument;
    private final DenseSubspaceFunction<NAMETYPE> function;

    private MapSubspaces(TensorFunction<NAMETYPE> argument, DenseSubspaceFunction<NAMETYPE> function) {
        this.argument = argument;
        this.function = function;
    }
    public MapSubspaces(TensorFunction<NAMETYPE> argument, String functionArg, TensorFunction<NAMETYPE> function) {
        this(argument, new DenseSubspaceFunction<>(functionArg, function));
        Objects.requireNonNull(argument, "The argument cannot be null");
        Objects.requireNonNull(functionArg, "The functionArg cannot be null");
        Objects.requireNonNull(function, "The function cannot be null");
    }

    private TensorType outputType(TensorType inputType) {
        var m = inputType.mappedSubtype();
        var d = function.outputType(inputType.indexedSubtype());
        if (m.rank() == 0) {
            return d;
        }
        if (d.rank() == 0) {
            return TypeResolver.map(m); // decay cell type
        }
        TensorType.Value cellType = d.valueType();
        Map<String, TensorType.Dimension> dims = new HashMap<>();
        for (var dim : m.dimensions()) {
            dims.put(dim.name(), dim);
        }
        for (var dim : d.dimensions()) {
            var old = dims.put(dim.name(), dim);
            if (old != null) {
                throw new IllegalArgumentException("dimension name collision in map_subspaces: " + m + " vs " + d);
            }
        }
        return new TensorType(cellType, dims.values());
    }

    public TensorFunction<NAMETYPE> argument() { return argument; }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("MapSubspaces must have 1 argument, got " + arguments.size());
        return new MapSubspaces<NAMETYPE>(arguments.get(0), function);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        return new MapSubspaces<>(argument.toPrimitive(), function);
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
                    mapAddrBuilder.add(dim.name(), fullAddr.label(i));
                } else {
                    idxAddrBuilder.add(dim.name(), fullAddr.label(i));
                }
            }
            var mapAddr = mapAddrBuilder.build();
            var builder = builders.computeIfAbsent(mapAddr, k -> Tensor.Builder.of(inputTypeDense));
            var idxAddr = idxAddrBuilder.build();
            builder.cell(idxAddr, cell.getValue());
        }
        TensorType outputType = outputType(input.type());
        TensorType denseOutputType = outputType.indexedSubtype();
        var denseOutputDims = denseOutputType.dimensions();
        Tensor.Builder builder = Tensor.Builder.of(outputType);
        for (var entry : builders.entrySet()) {
            TensorAddress mappedAddr = entry.getKey();
            Tensor denseInput = entry.getValue().build();
            Tensor denseOutput = function.map(denseInput);
            // XXX check denseOutput.type().dimensions()
            for (Iterator<Tensor.Cell> iter = denseOutput.cellIterator(); iter.hasNext(); ) {
                var cell = iter.next();
                var denseAddr = cell.getKey();
                var addrBuilder = new TensorAddress.Builder(outputType);
                for (int i = 0; i < inputTypeMapped.dimensions().size(); i++) {
                    var dim = inputTypeMapped.dimensions().get(i);
                    addrBuilder.add(dim.name(), mappedAddr.label(i));
                }
                for (int i = 0; i < denseOutputDims.size(); i++) {
                    var dim = denseOutputDims.get(i);
                    addrBuilder.add(dim.name(), denseAddr.label(i));
                }
                builder.cell(addrBuilder.build(), cell.getValue());
            }
        }
        return builder.build();
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "map_subspaces(" + argument.toString(context) + ", " + function + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("map_subspaces", argument, function); }

}
