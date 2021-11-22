// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An evaluator which can be used to evaluate a model with multiple outputs.
 * This will ensure that ONNX models are only evaluated once.
 *
 * @author lesters
 */
public class MultiFunctionEvaluator {

    private final List<FunctionEvaluator> functions;
    private boolean evaluated = false;

    MultiFunctionEvaluator(List<FunctionEvaluator> functions) {
        this.functions = functions;
    }

    /**
     * Binds the given variable referred in this expression to the given value.
     *
     * @param name the variable to bind
     * @param value the value this becomes bound to
     * @return this for chaining
     */
    public MultiFunctionEvaluator bind(String name, Tensor value) {
        if (evaluated)
            throw new IllegalStateException("Cannot bind a new value in a used evaluator");
        for (FunctionEvaluator function : functions) {
            if (function.function().argumentTypes().containsKey(name)) {
                function.bind(name, value);  // only bind input to the functions that need them
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
    public MultiFunctionEvaluator bind(String name, double value) {
        return bind(name, Tensor.Builder.of(TensorType.empty).cell(value).build());
    }

    public Map<String, Tensor> evaluate() {
        for (FunctionEvaluator function : functions) {
            function.checkArguments();
        }

        evaluateOnnxModels();  // evaluate each ONNX model only once

        Map<String, Tensor> results = new HashMap<>();
        for (FunctionEvaluator function : functions) {
            results.put(function.function().getName(), function.evaluate());
        }
        evaluated = true;
        return results;
    }

    /**
     * Evaluate all ONNX models across all functions once and add the result
     * back to the functions' context.
     */
    private void evaluateOnnxModels() {
        Set<OnnxModel> onnxModels = new HashSet<>();
        for (FunctionEvaluator function : functions) {
            onnxModels.addAll(function.context().onnxModels().values());
        }

        for (OnnxModel onnxModel : onnxModels) {

            // Gather inputs from all functions. Inputs with the same name must have the same value.
            Map<String, Tensor> inputs = new HashMap<>();
            for (FunctionEvaluator function : functions) {
                for (OnnxModel functionModel : function.context().onnxModels().values()) {
                    if (functionModel.name().equals(onnxModel.name())) {
                        for (String inputName: onnxModel.inputs().keySet()) {
                            inputs.put(inputName, function.context().get(inputName).asTensor());
                        }
                    }
                }
            }

            // Evaluate model once.
            Map<String, Tensor> outputs = onnxModel.evaluate(inputs);

            // Add outputs back to the context of the functions that need them; they won't be recalculated.
            for (FunctionEvaluator function : functions) {
                for (Map.Entry<String, OnnxModel> entry : function.context().onnxModels().entrySet()) {
                    String onnxFeature = entry.getKey();
                    OnnxModel functionModel = entry.getValue();
                    if (functionModel.name().equals(onnxModel.name())) {
                        Tensor result = outputs.get(function.function().getName());  // Function name is output of model
                        function.context().put(onnxFeature, new TensorValue(result));
                    }
                }
            }

        }
    }

    public List<FunctionEvaluator> functions() {
        return functions;
    }

}
