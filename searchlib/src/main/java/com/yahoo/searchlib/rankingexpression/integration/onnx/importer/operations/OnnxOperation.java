package com.yahoo.searchlib.rankingexpression.integration.onnx.importer.operations;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.functions.TensorFunction;
import onnx.Onnx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Wraps an ONNX node and produces the respective Vespa tensor operation.
 * During import, a graph of these operations are constructed. Then, the
 * types are used to deduce sensible dimension names using the
 * DimensionRenamer. After the types have been renamed, the proper
 * Vespa expressions can be extracted.
 *
 * @author lesters
 */
public abstract class OnnxOperation {

    protected final Onnx.NodeProto node; // can be null for onnx inputs and constants
    protected final List<OnnxOperation> inputs;
    protected final List<OnnxOperation> outputs = new ArrayList<>();
    protected final List<String> importWarnings = new ArrayList<>();

    protected OrderedTensorType type;
    protected TensorFunction function;
    protected Value constantValue = null;

    OnnxOperation(Onnx.NodeProto node, List<OnnxOperation> inputs) {
        this.node = node;
        this.inputs = Collections.unmodifiableList(inputs);
        this.inputs.forEach(i -> i.outputs.add(this));
    }

    protected abstract OrderedTensorType lazyGetType();
    protected abstract TensorFunction lazyGetFunction();

    /** Returns the Vespa tensor type of this operation if it exists */
    public Optional<OrderedTensorType> type() {
        if (type == null) {
            type = lazyGetType();
        }
        return Optional.ofNullable(type);
    }

    /** Returns the Vespa tensor function implementing all operations from this node with inputs */
    public Optional<TensorFunction> function() {
        if (function == null) {
            if (isConstant()) {
                ExpressionNode constant = new ReferenceNode(Reference.simple("constant", vespaName()));
                function = new TensorFunctionNode.TensorFunctionExpressionNode(constant);
            } else {
                function = lazyGetFunction();
            }
        }
        return Optional.ofNullable(function);
    }

    /** Return Onnx node */
    public Onnx.NodeProto node() { return node; }

    /** Return unmodifiable list of inputs */
    public List<OnnxOperation> inputs() { return inputs; }

    /** Return unmodifiable list of outputs. If a node has multiple outputs, consider adding a macro. */
    public List<OnnxOperation> outputs() { return Collections.unmodifiableList(outputs); }

    /** Add dimension name constraints for this operation */
    public void addDimensionNameConstraints(DimensionRenamer renamer) { }

    /** Performs dimension rename for this operation */
    public void renameDimensions(DimensionRenamer renamer) { type = type.rename(renamer); }

    /** Return true for operations that are inputs to the model itself (as opposed to inputs to the operation) */
    public boolean isInput() { return false; }

    /** Return true if this node is constant */
    public boolean isConstant() { return inputs.stream().allMatch(OnnxOperation::isConstant); }

    /** Gets the constant value if it exists */
    public Optional<Value> getConstantValue() { return Optional.ofNullable(constantValue); }

    /** Retrieve the valid Vespa name of this node */
    public String vespaName() { return vespaName(node.getName()); }
    public String vespaName(String name) { return name != null ? namePartOf(name).replace('/', '_') : null; }

    /** Retrieve the list of warnings produced during its lifetime */
    public List<String> warnings() { return Collections.unmodifiableList(importWarnings); }

    /** Set an input warning */
    public void warning(String warning) { importWarnings.add(warning); }

    boolean verifyInputs(int expected, Function<OnnxOperation, Optional<?>> func) {
        if (inputs.size() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " inputs " +
                    "for '" + node.getName() + "', got " + inputs.size());
        }
        return inputs.stream().map(func).allMatch(Optional::isPresent);
    }

    boolean allInputTypesPresent(int expected) {
        return verifyInputs(expected, OnnxOperation::type);
    }

    boolean allInputFunctionsPresent(int expected) {
        return verifyInputs(expected, OnnxOperation::function);
    }

    /**
     * A method signature input and output has the form name:index.
     * This returns the name part without the index.
     */
    public static String namePartOf(String name) {
        name = name.startsWith("^") ? name.substring(1) : name;
        return name.split(":")[0];
    }

    /**
     * This return the output index part. Indexes are used for nodes with
     * multiple outputs.
     */
    public static int indexPartOf(String name) {
        int i = name.indexOf(":");
        return i < 0 ? 0 : Integer.parseInt(name.substring(i + 1));
    }

}
