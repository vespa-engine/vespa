package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.tensor.MapTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The <i>rename</i> tensor function returns a tensor where some dimensions are assigned new names.
 * 
 * @author bratseth
 */
public class Rename extends PrimitiveTensorFunction {

    private final TensorFunction argument;
    private final List<String> fromDimensions;
    private final List<String> toDimensions;

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
    }
    
    @Override
    public List<TensorFunction> functionArguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction replaceArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Rename must have 1 argument, got " + arguments.size());
        return new Rename(arguments.get(0), fromDimensions, toDimensions);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() { return this; }

    @Override
    public Tensor evaluate(EvaluationContext context) {
        Tensor tensor = argument.evaluate(context);
        Map<String, String> fromToMap = fromToMap();
        Set<String> renamedDimensions = tensor.type().dimensions().stream()
                                                                  .map((d) -> fromToMap.getOrDefault(d.name(), d.name()))
                                                                  .collect(Collectors.toSet());
        
        ImmutableMap.Builder<TensorAddress, Double> renamedCells = new ImmutableMap.Builder<>();
        for (Map.Entry<TensorAddress, Double> cell : tensor.cells().entrySet()) {
            TensorAddress renamedAddress = rename(cell.getKey(), fromToMap);
            renamedCells.put(renamedAddress, cell.getValue());
        }
        return new MapTensor(asMappedDimensions(renamedDimensions), renamedCells.build());
    }

    private TensorType asMappedDimensions(Set<String> dimensionNames) {
        TensorType.Builder builder = new TensorType.Builder();
        for (String dimensionName : dimensionNames)
            builder.mapped(dimensionName);
        return builder.build();
    }

    private TensorAddress rename(TensorAddress address, Map<String, String> fromToMap) {
        List<TensorAddress.Element> renamedElements = new ArrayList<>();
        for (TensorAddress.Element element : address.elements()) {
            String toDimension = fromToMap.get(element.dimension());
            if (toDimension == null)
                renamedElements.add(element);
            else
                renamedElements.add(new TensorAddress.Element(toDimension, element.label()));
        }
        return TensorAddress.fromUnsorted(renamedElements);
    }

    @Override
    public String toString(ToStringContext context) { 
        return "rename(" + argument.toString(context) + ", " + 
                       toVectorString(fromDimensions) + ", " + toVectorString(toDimensions) + ")";
    }
    
    private Map<String, String> fromToMap() {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < fromDimensions.size(); i++)
            map.put(fromDimensions.get(i), toDimensions.get(i));
        return map;
    }
    
    private String toVectorString(List<String> elements) {
        if (elements.size() == 1)
            return elements.get(0);
        StringBuilder b = new StringBuilder("[");
        for (String element : elements)
            b.append(element).append(", ");
        b.setLength(b.length() - 2);
        return b.toString();
    }

}
