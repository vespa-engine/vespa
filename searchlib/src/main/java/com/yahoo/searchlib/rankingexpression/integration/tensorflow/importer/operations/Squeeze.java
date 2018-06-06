// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.TensorConverter;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.NodeDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Squeeze extends TensorFlowOperation {

    private List<String> squeezeDimensions;

    public Squeeze(String modelName, NodeDef node, List<TensorFlowOperation> inputs, int port) {
        super(modelName, node, inputs, port);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(1)) {
            return null;
        }
        OrderedTensorType inputType = inputs.get(0).type().get();
        squeezeDimensions = new ArrayList<>();

        AttrValue squeezeDimsAttr = node.getAttrMap().get("squeeze_dims");
        if (squeezeDimsAttr == null) {
            squeezeDimensions = inputType.type().dimensions().stream().
                    filter(dim -> TensorConverter.dimensionSize(dim) == 1).
                    map(TensorType.Dimension::name).
                    collect(Collectors.toList());
        } else {
            squeezeDimensions = squeezeDimsAttr.getList().getIList().stream().
                    map(i -> i < 0 ? inputType.type().dimensions().size() - i : i).
                    map(i -> inputType.type().dimensions().get(i.intValue())).
                    filter(dim -> TensorConverter.dimensionSize(dim) == 1).
                    map(TensorType.Dimension::name).
                    collect(Collectors.toList());
        }
        return squeezeDimensions.isEmpty() ? inputType : reducedType(inputType);
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        if (!allInputFunctionsPresent(1)) {
            return null;
        }
        TensorFunction inputFunction = inputs.get(0).function().get();
        return new Reduce(inputFunction, Reduce.Aggregator.sum, squeezeDimensions);
    }

    @Override
    public void renameDimensions(DimensionRenamer renamer) {
        super.renameDimensions(renamer);
        List<String> renamedDimensions = new ArrayList<>(squeezeDimensions.size());
        for (String name : squeezeDimensions) {
            Optional<String> newName = renamer.dimensionNameOf(name);
            if (!newName.isPresent()) {
                return;  // presumably, already renamed
            }
            renamedDimensions.add(newName.get());
        }
        squeezeDimensions = renamedDimensions;
    }

    private OrderedTensorType reducedType(OrderedTensorType inputType) {
        OrderedTensorType.Builder builder = new OrderedTensorType.Builder(node);
        for (TensorType.Dimension dimension: inputType.type().dimensions()) {
            if ( ! squeezeDimensions.contains(dimension.name())) {
                builder.add(dimension);
            }
        }
        return builder.build();
    }

}
