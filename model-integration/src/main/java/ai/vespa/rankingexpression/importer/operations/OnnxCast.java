// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.functions.TensorFunction;
import onnx.Onnx.TensorProto.DataType;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

public class OnnxCast extends IntermediateOperation {

    private final AttributeMap attributeMap;
    private final DataType toType;

    public OnnxCast(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributeMap) {
        super(modelName, nodeName, inputs);
        this.attributeMap = attributeMap;
        if (attributeMap.get("to").isEmpty()) {
            throw new IllegalArgumentException("OnnxCast in " + name + ": Required attribute 'to' is missing.");
        }
        toType = DataType.forNumber((int) attributeMap.get("to").get().asDouble());
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(1))
            return null;
        return inputs.get(0).type().orElse(null);
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! allInputFunctionsPresent(1))
            return null;
        TensorFunction<Reference> input = inputs.get(0).function().get();
        switch (toType) {
            case BOOL:
                return new com.yahoo.tensor.functions.Map<>(input, new AsBool());
            case INT8:
            case INT16:
            case INT32:
            case INT64:
            case UINT8:
            case UINT16:
            case UINT32:
            case UINT64:
                return new com.yahoo.tensor.functions.Map<>(input, new AsInt());
            case FLOAT:
            case DOUBLE:
            case FLOAT16:
                return input;
            case STRING:
                throw new IllegalArgumentException("OnnxCast in " + name + ": Casting to string is not implemented.");
            default:
                throw new IllegalArgumentException("OnnxCast in " + name + ": Unknown or undefined cast: " + toType.name());
        }
    }

    @Override
    public OnnxCast withInputs(List<IntermediateOperation> inputs) {
        return new OnnxCast(modelName(), name(), inputs, attributeMap);
    }

    @Override
    public String operationName() { return "Cast"; }

    private static class AsBool implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return operand != 0.0 ? 1 : 0; }
        @Override
        public String toString() { return "f(a)(a!=0)"; }
    }

    private static class AsInt implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return operand < 0 ? Math.ceil(operand) : Math.floor(operand); }
        @Override
        public String toString() { return "f(a)(if (a < 0, ceil(a), floor(a)))"; }
    }

}
