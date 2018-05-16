package com.yahoo.searchlib.rankingexpression.integration.onnx.importer;

import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.operations.Join;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.operations.MatMul;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.operations.NoOp;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.operations.OnnxOperation;
import com.yahoo.tensor.functions.ScalarFunctions;
import onnx.Onnx;

import java.util.List;

public class OperationMapper {

    public static OnnxOperation get(Onnx.NodeProto node, List<OnnxOperation> inputs) {
        switch (node.getOpType().toLowerCase()) {
            case "add":         return new Join(node, inputs, ScalarFunctions.add());
            case "matmul":      return new MatMul(node, inputs);
        }

        OnnxOperation op = new NoOp(node, inputs);
        op.warning("Operation '" + node.getOpType() + "' is currently not implemented");
        return op;
    }
}
