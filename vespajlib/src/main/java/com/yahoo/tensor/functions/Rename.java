// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The <i>rename</i> tensor function returns a tensor where some dimensions are assigned new names.
 *
 * @author bratseth
 */
@Beta
public class Rename extends PrimitiveTensorFunction {

    private final TensorFunction argument;
    private final List<String> fromDimensions;
    private final List<String> toDimensions;
    private final Map<String, String> fromToMap;

    public Rename(TensorFunction argument, String fromDimension, String toDimension) {
        this(argument, ImmutableList.of(fromDimension), ImmutableList.of(toDimension));
    }

    public Rename(TensorFunction argument, List<String> fromDimensions, List<String> toDimensions) {
        Objects.requireNonNull(argument, "The argument tensor cannot be null");
        Objects.requireNonNull(fromDimensions, "The 'from' dimensions cannot be null");
        Objects.requireNonNull(toDimensions, "The 'to' dimensions cannot be null");
        if (fromDimensions.size() < 1)
            throw new IllegalArgumentException("from dimensions is empty, must rename at least one dimension");
        if (fromDimensions.size() != toDimensions.size())
            throw new IllegalArgumentException("Rename from and to dimensions must be equal, was " +
                                               fromDimensions.size() + " and " + toDimensions.size());
        this.argument = argument;
        this.fromDimensions = ImmutableList.copyOf(fromDimensions);
        this.toDimensions = ImmutableList.copyOf(toDimensions);
        this.fromToMap = fromToMap(fromDimensions, toDimensions);
    }

    public List<String> fromDimensions() { return fromDimensions; }
    public List<String> toDimensions() { return toDimensions; }

    private static Map<String, String> fromToMap(List<String> fromDimensions, List<String> toDimensions) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < fromDimensions.size(); i++)
            map.put(fromDimensions.get(i), toDimensions.get(i));
        return map;
    }

    @Override
    public List<TensorFunction> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Rename must have 1 argument, got " + arguments.size());
        return new Rename(arguments.get(0), fromDimensions, toDimensions);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() { return this; }

    @Override
    public <NAMETYPE extends TypeContext.Name> TensorType type(TypeContext<NAMETYPE> context) {
        return type(argument.type(context));
    }

    private TensorType type(TensorType type) {
        TensorType.Builder builder = new TensorType.Builder();
        for (TensorType.Dimension dimension : type.dimensions())
            builder.dimension(dimension.withName(fromToMap.getOrDefault(dimension.name(), dimension.name())));
        return builder.build();
    }

    @Override
    public <NAMETYPE extends TypeContext.Name> Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor tensor = argument.evaluate(context);

        TensorType renamedType = type(tensor.type());

        // an array which lists the index of each label in the renamed type
        int[] toIndexes = new int[tensor.type().dimensions().size()];
        for (int i = 0; i < tensor.type().dimensions().size(); i++) {
            String dimensionName = tensor.type().dimensions().get(i).name();
            String newDimensionName = fromToMap.getOrDefault(dimensionName, dimensionName);
            toIndexes[i] = renamedType.indexOfDimension(newDimensionName).get();
        }

        Tensor.Builder builder = Tensor.Builder.of(renamedType);
        for (Iterator<Tensor.Cell> i = tensor.cellIterator(); i.hasNext(); ) {
            Map.Entry<TensorAddress, Double> cell = i.next();
            TensorAddress renamedAddress = rename(cell.getKey(), toIndexes);
            builder.cell(renamedAddress, cell.getValue());
        }
        return builder.build();
    }

    private TensorAddress rename(TensorAddress address, int[] toIndexes) {
        String[] reorderedLabels = new String[toIndexes.length];
        for (int i = 0; i < toIndexes.length; i++)
            reorderedLabels[toIndexes[i]] = address.label(i);
        return TensorAddress.of(reorderedLabels);
    }

    @Override
    public String toString(ToStringContext context) {
        return "rename(" + argument.toString(context) + ", " +
                       toVectorString(fromDimensions) + ", " + toVectorString(toDimensions) + ")";
    }

    private String toVectorString(List<String> elements) {
        if (elements.size() == 1)
            return elements.get(0);
        StringBuilder b = new StringBuilder("(");
        for (String element : elements)
            b.append(element).append(", ");
        b.setLength(b.length() - 2);
        b.append(")");
        return b.toString();
    }

}
