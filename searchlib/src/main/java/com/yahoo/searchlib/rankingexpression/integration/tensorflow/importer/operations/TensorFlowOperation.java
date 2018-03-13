// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.NodeDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Wraps a TensorFlow node and produces the respective Vespa tensor operation.
 * During import, a graph of these operations are constructed. Then, the
 * types are used to deduce sensible dimension names using the
 * DimensionRenamer. After the types have been renamed, the proper
 * Vespa expressions can be extracted.
 *
 * @author lesters
 */
public abstract class TensorFlowOperation {

    protected final static String MACRO_PREFIX = "tf_macro_";

    private final String modelName;

    protected final NodeDef node;
    protected final int port;
    protected final List<TensorFlowOperation> inputs;
    protected final List<TensorFlowOperation> outputs = new ArrayList<>();
    protected final List<String> importWarnings = new ArrayList<>();

    protected OrderedTensorType type;
    protected TensorFunction function;
    protected TensorFunction macro = null;

    private Value constantValue = null;
    private List<TensorFlowOperation> controlInputs = Collections.emptyList();

    TensorFlowOperation(String modelName, NodeDef node, List<TensorFlowOperation> inputs, int port) {
        this.modelName = modelName;
        this.node = node;
        this.port = port;
        this.inputs = Collections.unmodifiableList(inputs);
        this.inputs.forEach(i -> i.outputs.add(this));
    }

    protected String modelName() { return modelName; }

    protected abstract OrderedTensorType lazyGetType();
    protected abstract TensorFunction lazyGetFunction();

    /** Returns the Vespa tensor type of this operation if it exists */
    public Optional<OrderedTensorType> type() {
        if (type == null) {
            type = lazyGetType();
        }
        if (type != null) {
            type.verifyType(node);
        }
        return Optional.ofNullable(type);
    }

    /** Returns the Vespa tensor function implementing all operations from this node with inputs */
    public Optional<TensorFunction> function() {
        if (function == null) {
            if (isConstant()) {
                ExpressionNode constant = new ReferenceNode(Reference.simple("constant", vespaName()));
                function = new TensorFunctionNode.TensorFunctionExpressionNode(constant);
            } else if (outputs.size() > 1) {
                macro = lazyGetFunction();
                function = new VariableTensor(macroName(), type.type());
            } else {
                function = lazyGetFunction();
            }
        }
        return Optional.ofNullable(function);
    }

    /** Return TensorFlow node */
    public NodeDef node() { return node; }

    /** Return unmodifiable list of inputs */
    public List<TensorFlowOperation> inputs() { return inputs; }

    /** Return unmodifiable list of outputs. If a node has multiple outputs, consider adding a macro. */
    public List<TensorFlowOperation> outputs() { return Collections.unmodifiableList(outputs); }

    /** Returns a Vespa ranking expression that should be added as a macro */
    public Optional<TensorFunction> macro() { return Optional.ofNullable(macro); }

    /** Add dimension name constraints for this operation */
    public void addDimensionNameConstraints(DimensionRenamer renamer) { }

    /** Performs dimension rename for this operation */
    public void renameDimensions(DimensionRenamer renamer) { type = type.rename(renamer); }

    /** Return true for operations that are inputs to the model itself (as opposed to inputs to the operation) */
    public boolean isInput() { return false; }

    /** Return true if this node is constant */
    public boolean isConstant() { return inputs.stream().allMatch(TensorFlowOperation::isConstant); }

    /** Sets the constant value */
    public void setConstantValue(Value value) { constantValue = value; }

    /** Gets the constant value if it exists */
    public Optional<Value> getConstantValue() { return Optional.ofNullable(constantValue); }

    /** Sets the external control inputs */
    public void setControlInputs(List<TensorFlowOperation> inputs) { this.controlInputs = inputs; }

    /** Retrieve the control inputs for this operation */
    public List<TensorFlowOperation> getControlInputs() { return Collections.unmodifiableList(this.controlInputs); }

    /** Retrieve the valid Vespa name of this node */
    public String vespaName() { return node.getName() != null ? node.getName().replace('/', '_') : null; }

    /** Retrieve the valid Vespa name of this node if it is a macro */
    public String macroName() { return vespaName() != null ? MACRO_PREFIX + modelName + "_" + vespaName() : null; }

    /** Retrieve the list of warnings produced during its lifetime */
    public List<String> warnings() { return Collections.unmodifiableList(importWarnings); }

    boolean verifyInputs(int expected, Function<TensorFlowOperation, Optional<?>> func) {
        if (!controlInputs.stream().map(func).allMatch(Optional::isPresent)) {
            return false;
        }
        if (inputs.size() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " inputs " +
                                               "for '" + node.getName() + "', got " + inputs.size());
        }
        return inputs.stream().map(func).allMatch(Optional::isPresent);
    }

    boolean allInputTypesPresent(int expected) {
        return verifyInputs(expected, TensorFlowOperation::type);
    }

    boolean allInputFunctionsPresent(int expected) {
        return verifyInputs(expected, TensorFlowOperation::function);
    }

}
