// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer;

import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.ConcatV2;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Const;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.ExpandDims;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Identity;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Join;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Map;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Matmul;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Mean;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Merge;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.NoOp;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Placeholder;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.PlaceholderWithDefault;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Reshape;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Select;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Shape;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Squeeze;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Switch;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.TensorFlowOperation;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Variable;
import com.yahoo.tensor.functions.ScalarFunctions;
import org.tensorflow.framework.NodeDef;

import java.util.List;

/**
 * Maps from TensorFlow operations to Vespa operations.
 *
 * @author bratseth
 * @author lesters
 */
public class OperationMapper {

    public static TensorFlowOperation get(String modelName, NodeDef node, List<TensorFlowOperation> inputs, int port) {
        switch (node.getOp().toLowerCase()) {
            // array ops
            case "concatv2":    return new ConcatV2(modelName, node, inputs, port);
            case "const":       return new Const(modelName, node, inputs, port);
            case "expanddims":  return new ExpandDims(modelName, node, inputs, port);
            case "identity":    return new Identity(modelName, node, inputs, port);
            case "placeholder": return new Placeholder(modelName, node, inputs, port);
            case "placeholderwithdefault": return new PlaceholderWithDefault(modelName, node, inputs, port);
            case "reshape":     return new Reshape(modelName, node, inputs, port);
            case "shape":       return new Shape(modelName, node, inputs, port);
            case "squeeze":     return new Squeeze(modelName, node, inputs, port);

            // control flow
            case "merge":       return new Merge(modelName, node, inputs, port);
            case "switch":      return new Switch(modelName, node, inputs, port);

            // math ops
            case "add":         return new Join(modelName, node, inputs, port, ScalarFunctions.add());
            case "add_n":       return new Join(modelName, node, inputs, port, ScalarFunctions.add());
            case "acos":        return new Map(modelName, node, inputs, port, ScalarFunctions.acos());
            case "div":         return new Join(modelName, node, inputs, port, ScalarFunctions.divide());
            case "realdiv":     return new Join(modelName, node, inputs, port, ScalarFunctions.divide());
            case "floor":       return new Map(modelName, node, inputs, port, ScalarFunctions.floor());
            case "matmul":      return new Matmul(modelName, node, inputs, port);
            case "maximum":     return new Join(modelName, node, inputs, port, ScalarFunctions.max());
            case "mean":        return new Mean(modelName, node, inputs, port);
            case "reducemean":  return new Mean(modelName, node, inputs, port);
            case "mul":         return new Join(modelName, node, inputs, port, ScalarFunctions.multiply());
            case "multiply":    return new Join(modelName, node, inputs, port, ScalarFunctions.multiply());
            case "rsqrt":       return new Map(modelName, node, inputs, port, ScalarFunctions.rsqrt());
            case "select":      return new Select(modelName, node, inputs, port);
            case "where3":      return new Select(modelName, node, inputs, port);
            case "sigmoid":     return new Map(modelName, node, inputs, port, ScalarFunctions.sigmoid());
            case "squareddifference": return new Join(modelName, node, inputs, port, ScalarFunctions.squareddifference());
            case "sub":         return new Join(modelName, node, inputs, port, ScalarFunctions.subtract());
            case "subtract":    return new Join(modelName, node, inputs, port, ScalarFunctions.subtract());

            // nn ops
            case "biasadd":     return new Join(modelName, node, inputs, port, ScalarFunctions.add());
            case "elu":         return new Map(modelName, node, inputs, port, ScalarFunctions.elu());
            case "relu":        return new Map(modelName, node, inputs, port, ScalarFunctions.relu());
            case "selu":        return new Map(modelName, node, inputs, port, ScalarFunctions.selu());

            // state ops
            case "variable":    return new Variable(modelName, node, inputs, port);
            case "variablev2":  return new Variable(modelName, node, inputs, port);

            // evaluation no-ops
            case "stopgradient":return new Identity(modelName, node, inputs, port);
            case "noop":        return new NoOp(modelName, node, inputs, port);
        }
        return new NoOp(modelName, node, inputs, port);
    }

}



