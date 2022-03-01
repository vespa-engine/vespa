// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
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
import java.util.Map;
import java.util.Objects;

/**
 * The <i>rename</i> tensor function returns a tensor where some dimensions are assigned new names.
 *
 * @author bratseth
 */
public class Rename<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> argument;
    private final List<String> fromDimensions;
    private final List<String> toDimensions;
    private final Map<String, String> fromToMap;

    public Rename(TensorFunction<NAMETYPE> argument, String fromDimension, String toDimension) {
        this(argument, ImmutableList.of(fromDimension), ImmutableList.of(toDimension));
    }

    public Rename(TensorFunction<NAMETYPE> argument, List<String> fromDimensions, List<String> toDimensions) {
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
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Rename must have 1 argument, got " + arguments.size());
        return new Rename<>(arguments.get(0), fromDimensions, toDimensions);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() { return this; }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) {
        return type(argument.type(context));
    }

    private TensorType type(TensorType type) {
        return TypeResolver.rename(type, fromDimensions, toDimensions);
    }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor tensor = argument.evaluate(context);

        TensorType renamedType = type(tensor.type());

        // an array which lists the index of each label in the renamed type
        int[] toIndexes = new int[tensor.type().dimensions().size()];
        for (int i = 0; i < tensor.type().dimensions().size(); i++) {
            String dimensionName = tensor.type().dimensions().get(i).name();
            String newDimensionName = fromToMap.getOrDefault(dimensionName, dimensionName);
            toIndexes[i] = renamedType.indexOfDimension(newDimensionName).get();
        }

        // avoid building a new tensor if dimensions can simply be renamed
        if (simpleRenameIsPossible(toIndexes)) {
            return tensor.withType(renamedType);
        }

        Tensor.Builder builder = Tensor.Builder.of(renamedType);
        for (Iterator<Tensor.Cell> i = tensor.cellIterator(); i.hasNext(); ) {
            Map.Entry<TensorAddress, Double> cell = i.next();
            TensorAddress renamedAddress = rename(cell.getKey(), toIndexes);
            builder.cell(renamedAddress, cell.getValue());
        }
        return builder.build();
    }

    /**
     * If none of the dimensions change order after rename we can do a simple rename.
     */
    private boolean simpleRenameIsPossible(int[] toIndexes) {
        for (int i = 0; i < toIndexes.length; ++i) {
            if (toIndexes[i] != i) {
                return false;
            }
        }
        return true;
    }

    private TensorAddress rename(TensorAddress address, int[] toIndexes) {
        String[] reorderedLabels = new String[toIndexes.length];
        for (int i = 0; i < toIndexes.length; i++)
            reorderedLabels[toIndexes[i]] = address.label(i);
        return TensorAddress.of(reorderedLabels);
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

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "rename(" + argument.toString(context) + ", " +
                       toVectorString(fromDimensions) + ", " + toVectorString(toDimensions) + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("rename", argument, fromDimensions, toDimensions); }

}
