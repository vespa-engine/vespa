// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.evaluation.StringValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An evaluator which can be used to evaluate a function once.
 *
 * @author bratseth
 */
// This wraps all access to the context and the ranking expression to avoid incorrect usage
public class FunctionEvaluator {

    private final List<ExpressionFunction> functions;
    private final Map<String, LazyArrayContext> contexts;
    private final Map<String, Tensor> results;
    private boolean evaluated = false;

    FunctionEvaluator(ExpressionFunction function, LazyArrayContext context) {
        this(List.of(function), Map.of(function.getName(), context));
    }

    FunctionEvaluator(List<ExpressionFunction> functions, Map<String, LazyArrayContext> contexts) {
        this.functions = List.copyOf(functions);
        this.contexts = Map.copyOf(contexts);
        this.results = new HashMap<>();
    }

    public Tensor result(String name) {
        return results.get(name);
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
        for (ExpressionFunction function : functions) {
            if (function.argumentTypes().containsKey(name)) {
                TensorType requiredType = function.argumentTypes().get(name);
                if ( ! value.type().isAssignableTo(requiredType))
                    throw new IllegalArgumentException("'" + name + "' must be of type " + requiredType + ", not " + value.type());
                contexts.get(function.getName()).put(name, new TensorValue(value));
            }
        }
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
        for (ExpressionFunction function : functions) {
            if (function.argumentTypes().containsKey(name)) {
                contexts.get(function.getName()).put(name, new StringValue(value));
            }
        }
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
        for (LazyArrayContext context : contexts.values()) {
            context.setMissingValue(value);
        }
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
        checkArguments();
        evaluateOnnxModels();

        Tensor defaultResult = null;
        for (ExpressionFunction function: functions) {
            LazyArrayContext context = contexts.get(function.getName());
            Tensor result = function.getBody().evaluate(context).asTensor();
            results.put(function.getName(), function.getBody().evaluate(context).asTensor());
            if (defaultResult == null) {
                defaultResult = result;
            }
        }
        evaluated = true;
        return defaultResult;
    }

    void checkArguments() {
        for (ExpressionFunction function : functions) {
            LazyArrayContext context = contexts.get(function.getName());
            for (Map.Entry<String, TensorType> argument : function.argumentTypes().entrySet()) {
                checkArgument(argument.getKey(), argument.getValue(), context);
            }
        }
    }

    private void checkArgument(String name, TensorType type, LazyArrayContext context) {
        if (context.isMissing(name))
            throw new IllegalStateException("Missing argument '" + name + "': Must be bound to a value of type " + type);
        if (! context.get(name).type().isAssignableTo(type))
            throw new IllegalStateException("Argument '" + name + "' must be bound to a value of type " + type);
    }

    /**
     * Evaluate ONNX models (if not already evaluated) and add the result back to the context.
     */
    private void evaluateOnnxModels() {
        Set<OnnxModel> onnxModels = new HashSet<>();
        for (LazyArrayContext context : contexts.values()) {
            onnxModels.addAll(context.onnxModels().values());
        }

        for (OnnxModel onnxModel : onnxModels) {

            // Gather inputs from all functions. Inputs with the same name must have the same value.
            Map<String, Tensor> inputs = new HashMap<>();
            for (LazyArrayContext context : contexts.values()) {
                for (OnnxModel functionModel : context.onnxModels().values()) {
                    if (functionModel.name().equals(onnxModel.name())) {
                        for (String inputName: onnxModel.inputs().keySet()) {
                            inputs.put(inputName, context.get(inputName).asTensor());
                        }
                    }
                }
            }

            // Evaluate model once.
            Map<String, Tensor> outputs = onnxModel.evaluate(inputs);

            // Add outputs back to the context of the functions that need them; they won't be recalculated.
            for (ExpressionFunction function : functions) {
                LazyArrayContext context = contexts.get(function.getName());
                for (Map.Entry<String, OnnxModel> entry : context.onnxModels().entrySet()) {
                    String onnxFeature = entry.getKey();
                    OnnxModel functionModel = entry.getValue();
                    if (functionModel.name().equals(onnxModel.name())) {
                        Tensor result = outputs.get(function.getName());  // Function name is output of model
                        context.put(onnxFeature, new TensorValue(result));
                    }
                }
            }

        }
    }

    /** Returns the default function evaluated by this */
    public ExpressionFunction function() { return functions.get(0); }

    public LazyArrayContext context() { return contexts.get(function().getName()); }

    /** Returns the names of the outputs of this function */
    public List<String> outputs() {
        return functions.stream().map(ExpressionFunction::getName).collect(Collectors.toList());
    }

}
