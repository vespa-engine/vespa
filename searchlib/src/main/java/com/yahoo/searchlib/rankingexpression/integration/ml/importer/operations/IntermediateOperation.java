// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public abstract class IntermediateOperation {

    protected final static String MACRO_PREFIX = "imported_ml_macro_";

    protected final String name;
    protected final String modelName;
    protected final List<IntermediateOperation> inputs;
    protected final List<IntermediateOperation> outputs = new ArrayList<>();
    protected final List<String> importWarnings = new ArrayList<>();

    protected OrderedTensorType type;
    protected TensorFunction function;
    protected TensorFunction macro = null;
    protected Value constantValue = null;
    protected Function<OrderedTensorType, Value> constantValueFunction = null;
    protected List<IntermediateOperation> controlInputs = Collections.emptyList();

    IntermediateOperation(String modelName, String name, List<IntermediateOperation> inputs) {
        this.name = name;
        this.modelName = modelName;
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
            } else if (outputs.size() > 1) {
                macro = lazyGetFunction();
                function = new VariableTensor(macroName(), type.type());
            } else {
                function = lazyGetFunction();
            }
        }
        return Optional.ofNullable(function);
    }

    /** Returns original name of this operation node */
    public String name() { return name; }

    /** Return unmodifiable list of inputs */
    public List<IntermediateOperation> inputs() { return inputs; }

    /** Return unmodifiable list of outputs. If a node has multiple outputs, consider adding a macro. */
    public List<IntermediateOperation> outputs() { return Collections.unmodifiableList(outputs); }

    /** Returns a Vespa ranking expression that should be added as a macro */
    public Optional<TensorFunction> macro() { return Optional.ofNullable(macro); }

    /** Add dimension name constraints for this operation */
    public void addDimensionNameConstraints(DimensionRenamer renamer) { }

    /** Performs dimension rename for this operation */
    public void renameDimensions(DimensionRenamer renamer) { type = type.rename(renamer); }

    /** Return true for operations that are inputs to the model itself (as opposed to inputs to the operation) */
    public boolean isInput() { return false; }

    /** Return true if this node is constant */
    public boolean isConstant() { return inputs.stream().allMatch(IntermediateOperation::isConstant); }

    /** Sets the constant value */
    public void setConstantValue(Value value) { constantValue = value; }

    /** Gets the constant value if it exists */
    public Optional<Value> getConstantValue() {
        if (constantValue != null) {
            return Optional.of(constantValue);
        }
        if (constantValueFunction != null) {
            return Optional.of(constantValueFunction.apply(type));
        }
        return Optional.empty();
    }

    /** Set the constant value function */
    public void setConstantValueFunction(Function<OrderedTensorType, Value> func) { this.constantValueFunction = func; }

    /** Sets the external control inputs */
    public void setControlInputs(List<IntermediateOperation> inputs) { this.controlInputs = inputs; }

    /** Retrieve the control inputs for this operation */
    public List<IntermediateOperation> getControlInputs() { return Collections.unmodifiableList(this.controlInputs); }

    /** Retrieve the valid Vespa name of this node */
    public String vespaName() { return vespaName(name); }
    public String vespaName(String name) { return name != null ? namePartOf(name).replace('/', '_') : null; }

    /** Retrieve the valid Vespa name of this node if it is a macro */
    public String macroName() { return vespaName() != null ? MACRO_PREFIX + modelName + "_" + vespaName() : null; }

    /** Retrieve the list of warnings produced during its lifetime */
    public List<String> warnings() { return Collections.unmodifiableList(importWarnings); }

    /** Set an input warning */
    public void warning(String warning) { importWarnings.add(warning); }

    boolean verifyInputs(int expected, Function<IntermediateOperation, Optional<?>> func) {
        if (inputs.size() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " inputs " +
                    "for '" + name + "', got " + inputs.size());
        }
        return inputs.stream().map(func).allMatch(Optional::isPresent);
    }

    boolean allInputTypesPresent(int expected) {
        return verifyInputs(expected, IntermediateOperation::type);
    }

    boolean allInputFunctionsPresent(int expected) {
        return verifyInputs(expected, IntermediateOperation::function);
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

    /**
     * An interface mapping operation attributes to Vespa Values.
     * Adapter for differences in ONNX/TensorFlow.
     */
    public interface AttributeMap {
        Optional<Value> get(String key);
        Optional<Value> get(String key, OrderedTensorType type);
        Optional<List<Value>> getList(String key);
    }

}
