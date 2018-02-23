// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.NodeDef;

import java.util.List;

public class Placeholder extends TensorFlowOperation {

    private OrderedTensorType standardNamingType;  // using standard naming convention: d0, d1, ...

    public Placeholder(NodeDef node, List<TensorFlowOperation> inputs, int port) {
        super(node, inputs, port);
        standardNamingType = OrderedTensorType.fromTensorFlowType(node);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return OrderedTensorType.fromTensorFlowType(node, vespaName() + "_");
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        TensorFunction output = new VariableTensor(vespaName(), standardNamingType.type());
        if (!standardNamingType.equals(type)) {
            List<String> renameFrom = standardNamingType.dimensionNames();
            List<String> renameTo = type.dimensionNames();
            output = new Rename(output, renameFrom, renameTo);
        }
        return output;
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        for (TensorType.Dimension dimension : type.type().dimensions()) {
            renamer.addDimension(dimension.name());
        }
    }

    @Override
    public boolean isInput() {
        return true;
    }

    @Override
    public boolean isConstant() {
        return false;
    }


}
