// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.IntermediateGraph;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Wraps an imported operation node and produces the respective Vespa tensor
 * operation. During import, a graph of these operations are constructed. Then,
 * the types are used to deduce sensible dimension names using the
 * DimensionRenamer. After the types have been renamed, the proper Vespa
 * expressions can be extracted.
 *
 * @author lesters
 */
public abstract class IntermediateOperation {

    public final static String FUNCTION_PREFIX = "imported_ml_function_";

    protected final String name;
    protected final String modelName;
    protected final List<IntermediateOperation> inputs;
    protected final List<IntermediateOperation> outputs = new ArrayList<>();

    protected OrderedTensorType type;
    protected TensorFunction<Reference> function;
    protected TensorFunction<Reference> rankingExpressionFunction = null;
    protected boolean exportAsRankingFunction = false;

    private boolean hasRenamedDimensions = false;

    private final List<String> importWarnings = new ArrayList<>();
    private Value constantValue = null;
    private List<IntermediateOperation> controlInputs = Collections.emptyList();

    protected Function<OrderedTensorType, Value> constantValueFunction = null;

    IntermediateOperation(String modelName, String name, List<IntermediateOperation> inputs) {
        this.name = name;
        this.modelName = ensureValidAsDimensionName(modelName);
        this.inputs = new ArrayList<>(inputs);
        this.inputs.forEach(i -> i.outputs.add(this));
    }

    protected abstract OrderedTensorType lazyGetType();
    protected abstract TensorFunction<Reference> lazyGetFunction();

    public String modelName() { return modelName; }

    /** Returns the Vespa tensor type of this operation if it exists */
    public Optional<OrderedTensorType> type() {
        if (type == null) {
            type = lazyGetType();
        }
        return Optional.ofNullable(type);
    }

    /** Returns the Vespa tensor function implementing all operations from this node with inputs */
    public Optional<TensorFunction<Reference>> function() {
        if (function == null) {
            if (isConstant()) {
                ExpressionNode constant = new ReferenceNode(Reference.simple("constant", vespaName()));
                function = new TensorFunctionNode.ExpressionTensorFunction(constant);
            } else if (outputs.size() > 1 || exportAsRankingFunction) {
                rankingExpressionFunction = lazyGetFunction();
                function = new VariableTensor<Reference>(rankingExpressionFunctionName(), type.type());
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

    /** Return unmodifiable list of outputs. If a node has multiple outputs, consider adding a function. */
    public List<IntermediateOperation> outputs() { return Collections.unmodifiableList(outputs); }

    /** Returns a function that should be added as a ranking expression function */
    public Optional<TensorFunction<Reference>> rankingExpressionFunction() {
        return Optional.ofNullable(rankingExpressionFunction);
    }

    /** Add dimension name constraints for this operation */
    public void addDimensionNameConstraints(DimensionRenamer renamer) { }

    /** Convenience method to adds dimensions and constraints of the given tensor type */
    protected void addConstraintsFrom(OrderedTensorType type, DimensionRenamer renamer) {
        for (int i = 0; i < type.dimensions().size(); i++) {
            renamer.addDimension(type.dimensions().get(i).name());

            // Each dimension is distinct and ordered correctly:
            for (int j = i + 1; j < type.dimensions().size(); j++) {
                renamer.addConstraint(type.dimensions().get(i).name(), type.dimensions().get(j).name(),
                                      DimensionRenamer.Constraint.notEqual(false),
                                      this);
            }
        }
    }

    /** Performs dimension rename for this operation */
    public void renameDimensions(DimensionRenamer renamer) {
        type = type.rename(renamer);
        hasRenamedDimensions = true;
    }

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
            return Optional.of(constantValueFunction.apply(type().orElse(null)));
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
    public String vespaName() {
        if (isConstant())
            return modelName + "_" + vespaName(name);
        return vespaName(name);
    }

    public static String vespaName(String name) {
        return name != null ? namePartOf(name).replace('/', '_').replace('.', '_') : null;
    }

    /** Retrieve the valid Vespa name of this node if it is a ranking expression function */
    public String rankingExpressionFunctionName() {
        String vespaName = vespaName();
        if (vespaName == null) {
            return null;
        }
        return isConstant() ? "constant(" + vespaName + ")" : FUNCTION_PREFIX + modelName + "_" + vespaName;
    }

    /** Retrieve the list of warnings produced during its lifetime */
    public List<String> warnings() { return Collections.unmodifiableList(importWarnings); }

    /** Set an input warning */
    public void warning(String warning) { importWarnings.add(warning); }

    boolean verifyInputs(int expected, Function<IntermediateOperation, Optional<?>> func) {
        if (inputs.size() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " inputs for '" +
                                               name + "', got " + inputs.size());
        }
        return inputs.stream().map(func).allMatch(Optional::isPresent);
    }

    boolean allInputTypesPresent(int expected) {
        return verifyInputs(expected, IntermediateOperation::type);
    }

    boolean allInputFunctionsPresent(int expected) {
        return verifyInputs(expected, IntermediateOperation::function);
    }

    /** Recursively evaluates this operation's constant value to avoid doing it run-time. */
    public Value evaluateAsConstant(OrderedTensorType type) {
        if ( ! isConstant() ) {
            throw new IllegalArgumentException("Attempted to evaluate non-constant operation as a constant.");
        }
        Value val = evaluableCopy().evaluateAsConstant(new MapContext(DoubleValue.NaN));
        if (type == null) {
            return val;
        }
        Tensor tensor = val.asTensor();
        checkIfRenameableTo(tensor, type);
        setConstantValueFunction(t -> new TensorValue(tensor.withType(t.type())));  // so we don't have to re-evaluate
        return new TensorValue(tensor.withType(type.type()));
    }

    private void checkIfRenameableTo(Tensor tensor, OrderedTensorType type) {
        if ( ! tensor.type().isRenamableTo(type.type()) ) {
            throw new IllegalArgumentException("Constant evaluation in " + name + " resulted in wrong type. " +
                    "Expected: " + type.type() + " Got: " + tensor.type());
        }
    }

    private IntermediateOperation evaluableCopy() {
        if (hasRenamedDimensions) {
            return this;
        }
        IntermediateOperation copy = copyTree();

        // Must have performed dimension renaming to properly evaluate as constant
        IntermediateGraph graph = new IntermediateGraph(modelName);
        graph.put(name, copy);
        graph.outputs(graph.defaultSignature()).put(name, name);
        graph.optimize();

        return copy;
    }

    private IntermediateOperation copyTree() {
        List<IntermediateOperation> in = new ArrayList<>();
        if (constantValue != null) {
            IntermediateOperation constant = new Constant(modelName, name, type);
            constant.setConstantValueFunction(t -> new TensorValue(constantValue.asTensor().withType(t.type())));
            return constant;
        }
        inputs.forEach(i -> in.add(i.copyTree()));
        IntermediateOperation copy = withInputs(in);
        if (constantValueFunction != null) {
            copy.constantValueFunction = constantValueFunction;
        }
        return copy;
    }

    private Value evaluateAsConstant(Context context) {
        String constantName = "constant(" + vespaName() + ")";
        Value result = context.get(constantName);
        if (result == DoubleValue.NaN) {
            if (constantValue != null) {
                result = constantValue;
            } else if (inputs.size() == 0) {
                if (getConstantValue().isEmpty()) {
                    throw new IllegalArgumentException("Error in evaluating constant for " + name);
                }
                result = getConstantValue().get();
            } else {
                inputs.forEach(i -> i.evaluateAsConstant(context));
                result = new TensorValue(lazyGetFunction().evaluate(context));
            }
            context.put(constantName, result);
            if (outputs.size() > 1 || exportAsRankingFunction) {
                context.put(rankingExpressionFunctionName(), result);
            }
        }
        return result;
    }

    /** Insert an operation between an input and this one */
    public void insert(IntermediateOperation operationToInsert, int inputNumber) {
        if ( operationToInsert.inputs.size() > 0 ) {
            throw new IllegalArgumentException("Operation to insert to '" + name + "' has " +
                                               "existing inputs which is not supported.");
        }
        IntermediateOperation previousInputOperation = inputs.get(inputNumber);
        int outputNumber = findOutputNumber(previousInputOperation, this);
        if (outputNumber == -1) {
            throw new IllegalArgumentException("Input '" + previousInputOperation.name + "' to '" +
                                               name + "' does not have '" + name + "' as output.");
        }
        previousInputOperation.outputs.set(outputNumber, operationToInsert);
        operationToInsert.inputs.add(previousInputOperation);
        operationToInsert.outputs.add(this);
        inputs.set(inputNumber, operationToInsert);
    }

    /** Remove an operation between an input and this one */
    public void uninsert(int inputNumber) {
        IntermediateOperation operationToRemove = inputs.get(inputNumber);
        IntermediateOperation newInputOperation = operationToRemove.inputs.get(0);
        int outputNumber = findOutputNumber(newInputOperation, operationToRemove);
        newInputOperation.outputs.set(outputNumber, this);
        inputs.set(inputNumber, newInputOperation);
    }

    private int findOutputNumber(IntermediateOperation output, IntermediateOperation op) {
        for (int i = 0; i < output.outputs.size(); ++i) {
            if (output.outputs.get(i).equals(op)) {
                return i;
            }
        }
        return -1;
    }

    /** Removes outputs if they point to the same operation */
    public void removeDuplicateOutputsTo(IntermediateOperation op) {
        int last, first = outputs.indexOf(op);
        while (first >= 0 && (last = outputs.lastIndexOf(op)) > first) {
            outputs.remove(last);
        }
    }

    /**
     * Returns the largest value type among the input value types.
     * This should only be called after it has been verified that input types are available.
     *
     * @throws IllegalArgumentException if a type cannot be uniquely determined
     * @throws RuntimeException if called when input types are not available
     */
    TensorType.Value resultValueType() {
        return TensorType.Value.largestOf(inputs.stream()
                                                .map(input -> input.type().get().type().valueType())
                                                .collect(Collectors.toList()));
    }

    public abstract IntermediateOperation withInputs(List<IntermediateOperation> inputs);

    String asString(Optional<OrderedTensorType> type) {
        return type.map(t -> t.toString()).orElse("(unknown)");
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

    public abstract String operationName();

    /** Required due to tensor dimension name restrictions */
    private static String ensureValidAsDimensionName(String modelName) {
        return modelName.replaceAll("[^\\w\\d\\$@_]", "_");
    }

    @Override
    public String toString() {
        return operationName() + "(" +
               inputs().stream().map(input -> asString(input.type())).collect(Collectors.joining(", ")) +
               ")";
    }

    public String toFullString() {
        return "\t" + type + ":\t" + operationName() + "(" +
               inputs().stream().map(input -> input.toFullString()).collect(Collectors.joining(", ")) +
               ")";
    }

    /**
     * An interface mapping operation attributes to Vespa Values.
     * Adapter for differences in different model types.
     */
    public interface AttributeMap {
        Optional<Value> get(String key);
        Optional<Value> get(String key, OrderedTensorType type);
        Optional<List<Value>> getList(String key);
    }

}
