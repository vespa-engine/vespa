// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A named ONNX model that should be evaluated with OnnxEvaluator.
 *
 * @author lesters
 */
class OnnxModel implements AutoCloseable {

    static class InputSpec {
        String onnxName;
        String source;
        TensorType wantedType;
        InputSpec(String name, String source, TensorType tType) {
            this.onnxName = name;
            this.source = source;
            this.wantedType = tType;
        }
        InputSpec(String name, String source) { this(name, source, null); }
    }

    static class OutputSpec {
        String onnxName;
        String outputAs;
        TensorType expectedType;
        OutputSpec(String name, String as, TensorType tType) {
            this.onnxName = name;
            this.outputAs = as;
            this.expectedType = tType;
        }
        OutputSpec(String name, String as) { this(name, as, null); }
    }

    final List<InputSpec> inputSpecs = new ArrayList<>();
    final List<OutputSpec> outputSpecs = new ArrayList<>();

    void addInputMapping(String onnxName, String source) {
        if (evaluator != null)
            throw new IllegalStateException("input mapping must be added before load()");
        inputSpecs.add(new InputSpec(onnxName, source));
    }
    void addOutputMapping(String onnxName, String outputAs) {
        if (evaluator != null)
            throw new IllegalStateException("output mapping must be added before load()");
        outputSpecs.add(new OutputSpec(onnxName, outputAs));
    }

    private final String name;
    private final File modelFile;
    private final OnnxEvaluatorOptions options;
    private final OnnxRuntime onnx;

    private OnnxEvaluator evaluator;
    private final Map<String, ExpressionNode> exprPerOutput = new HashMap<>();

    OnnxModel(String name, File modelFile, OnnxEvaluatorOptions options, OnnxRuntime onnx) {
        this.name = name;
        this.modelFile = modelFile;
        this.options = options;
        this.onnx = onnx;
    }

    public String name() {
        return name;
    }

    public void load() {
        if (evaluator == null) {
            evaluator = onnx.evaluatorOf(modelFile.getPath(), options);
            fillInputTypes(evaluator().getInputs());
            fillOutputTypes(evaluator().getOutputs());
            fillOutputExpressions();
        }
    }

    void fillInputTypes(Map<String, OnnxEvaluator.IdAndType> wantedTypes) {
        if (inputSpecs.isEmpty()) {
            for (var entry : wantedTypes.entrySet()) {
                String name = entry.getKey();
                String source = entry.getValue().id();
                TensorType tType = entry.getValue().type();
                var spec = new InputSpec(name, source, tType);
                inputSpecs.add(spec);
            }
        } else {
            if (wantedTypes.size() != inputSpecs.size()) {
                throw new IllegalArgumentException("Onnx model " + name() +
                                                   ": Mismatch between " + inputSpecs.size() +
                                                   " configured inputs and " +
                                                   wantedTypes.size() + " actual model inputs");
            }
            for (var spec : inputSpecs) {
                var entry = wantedTypes.get(spec.onnxName);
                if (entry == null) {
                    throw new IllegalArgumentException("Onnx model " + name() +
                                                       ": No type in actual model for configured input "
                                                       + spec.onnxName);
                }
                spec.wantedType = entry.type();
            }
        }
    }

    void fillOutputTypes(Map<String, OnnxEvaluator.IdAndType> outputTypes) {
        if (outputSpecs.isEmpty()) {
            for (var entry : outputTypes.entrySet()) {
                String name = entry.getKey();
                String as = entry.getValue().id();
                TensorType tType = entry.getValue().type();
                var spec = new OutputSpec(name, as, tType);
                outputSpecs.add(spec);
            }
        } else {
            if (outputTypes.size() != outputSpecs.size()) {
                throw new IllegalArgumentException("Onnx model " + name() +
                                                   ": Mismatch between " + outputSpecs.size() +
                                                   " configured outputs and " +
                                                   outputTypes.size() + " actual model outputs");
            }
            for (var spec : outputSpecs) {
                var entry = outputTypes.get(spec.onnxName);
                if (entry == null) {
                    throw new IllegalArgumentException("Onnx model " + name() +
                                                       ": No type in actual model for configured output "
                                                       + spec.onnxName);
                }
                spec.expectedType = entry.type();
            }
        }
    }

    public Map<String, TensorType> inputs() {
        var map = new HashMap<String, TensorType>();
        for (var spec : inputSpecs) {
            map.put(spec.source, spec.wantedType);
        }
        return map;
    }

    public Map<String, TensorType> outputs() {
        var map = new HashMap<String, TensorType>();
        for (var spec : outputSpecs) {
            map.put(spec.outputAs, spec.expectedType);
        }
        return map;
    }

    void fillOutputExpressions() {
        for (var spec : outputSpecs) {
            var node = new OnnxExpressionNode(this, spec.onnxName, spec.expectedType, spec.outputAs);
            exprPerOutput.put(spec.outputAs, node);
        }
    }

    ExpressionNode getExpressionForOutput(String outputName) {
        if (outputName == null && exprPerOutput.size() == 1) {
            return exprPerOutput.values().iterator().next();
        }
        return exprPerOutput.get(outputName);
    }

    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        var mapped = new HashMap<String, Tensor>();
        for (var spec : inputSpecs) {
            Tensor val = inputs.get(spec.source);
            if (val == null) {
                throw new IllegalArgumentException("evaluate ONNX model " + name() + ": missing input from source " + spec.source);
            }
            mapped.put(spec.onnxName, val);
        }
        String onnxName = null;
        for (var spec : outputSpecs) {
            if (spec.outputAs.equals(output)) {
                onnxName = spec.onnxName;
            }
        }
        if (onnxName == null) {
            throw new IllegalArgumentException("evaluate ONNX model " + name() + ": no output available as: " + output);
        }
        return unmappedEvaluate(mapped, onnxName);
    }

    Tensor unmappedEvaluate(Map<String, Tensor> inputs, String onnxOutputName) {
        return evaluator().evaluate(inputs, onnxOutputName);
    }

    private OnnxEvaluator evaluator() {
        if (evaluator == null) {
            throw new IllegalStateException("ONNX model has not been loaded.");
        }
        return evaluator;
    }

    @Override public void close() { evaluator.close(); }
}
