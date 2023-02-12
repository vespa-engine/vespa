// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.evaluation.StringValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An evaluator which can be used to evaluate a single function once.
 *
 * @author bratseth
 */
// This wraps all access to the context and the ranking expression to avoid incorrect usage
public class FunctionEvaluator {

    private final ExpressionFunction function;
    private final LazyArrayContext context;
    private boolean evaluated = false;

    FunctionEvaluator(ExpressionFunction function, LazyArrayContext context) {
        this.function = function;
        this.context = context;
    }

    /**
     * Binds the given variable referred in this expression to the given value.
     *
     * @param name the variable to bind
     * @param value the value this becomes bound to
     * @return this for chaining
     */
    public FunctionEvaluator bind(String name, Tensor value) {
        if (evaluated)
            throw new IllegalStateException("Cannot bind a new value in a used evaluator");
        TensorType requiredType = function.getArgumentType(name);
        if (requiredType == null)
            throw new IllegalArgumentException("'" + name + "' is not a valid argument in " + function +
                                               ". Expected arguments: " +
                    function.argumentTypes().entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .map(e -> e.getKey() + ": " + e.getValue())
                            .collect(Collectors.joining(", ")));
        if ( ! value.type().isAssignableTo(requiredType))
            throw new IllegalArgumentException("'" + name + "' must be of type " + requiredType + ", not " + value.type());
        context.put(name, new TensorValue(value));
        return this;
    }

    /**
     * Binds the given variable referred in this expression to the given value.
     * This is equivalent to <code>bind(name, Tensor.Builder.of(TensorType.empty).cell(value).build())</code>
     *
     * @param name the variable to bind
     * @param value the value this becomes bound to
     * @return this for chaining
     */
    public FunctionEvaluator bind(String name, double value) {
        return bind(name, Tensor.Builder.of(TensorType.empty).cell(value).build());
    }

    /**
     * Binds the given variable referred in this expression to the given value.
     * String values are not yet supported in tensors.
     *
     * @param name the variable to bind
     * @param value the value this becomes bound to
     * @return this for chaining
     */
    public FunctionEvaluator bind(String name, String value) {
        if (evaluated)
            throw new IllegalStateException("Cannot bind a new value in a used evaluator");
        context.put(name, new StringValue(value));
        return this;
    }

    /**
     * Sets the default value to use for variables which are not bound
     *
     * @param value the default value
     * @return this for chaining
     */
    public FunctionEvaluator setMissingValue(Tensor value) {
        if (evaluated)
            throw new IllegalStateException("Cannot change the missing value in a used evaluator");
        context.setMissingValue(value);
        return this;
    }

    /**
     * Sets the default value to use for variables which are not bound
     *
     * @param value the default value
     * @return this for chaining
     */
    public FunctionEvaluator setMissingValue(double value) {
        return setMissingValue(Tensor.Builder.of(TensorType.empty).cell(value).build());
    }

    public Tensor evaluate() {
        function.argumentTypes().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(argument -> checkArgument(argument.getKey(), argument.getValue()));
        evaluated = true;
        evaluateOnnxModels();
        return function.getBody().evaluate(context).asTensor();
    }

    private void checkArgument(String name, TensorType type) {
        if (context.isMissing(name))
            throw new IllegalStateException("Missing argument '" + name + "': Must be bound to a value of type " + type);
        if (! context.get(name).type().isAssignableTo(type))
            throw new IllegalStateException("Argument '" + name + "' must be bound to a value of type " + type);
    }

    /**
     * Evaluate ONNX models (if not already evaluated) and add the result back to the context.
     */
    private void evaluateOnnxModels() {
        for (Map.Entry<String, OnnxModel> entry : context().onnxModels().entrySet()) {
            String onnxFeature = entry.getKey();
            String outputName = function.getName(); // Function name is output of model (sometimes)
            int idx = onnxFeature.indexOf(").");
            if (idx > 0 && idx + 2 < onnxFeature.length()) {
                // explicitly specified as onnx(modelname).outputname ; pick the last part
                outputName = onnxFeature.substring(idx+2);
            }
            OnnxModel onnxModel = entry.getValue();
            if (context.get(onnxFeature).equals(context.defaultValue())) {
                Map<String, Tensor> inputs = new HashMap<>();
                for (Map.Entry<String, TensorType> input: onnxModel.inputs().entrySet()) {
                    inputs.put(input.getKey(), context.get(input.getKey()).asTensor());
                }
                Tensor result = onnxModel.evaluate(inputs, outputName);
                context.put(onnxFeature, new TensorValue(result));
            }
        }
    }

    /** Returns the function evaluated by this */
    public ExpressionFunction function() { return function; }

    public LazyArrayContext context() { return context; }

}
