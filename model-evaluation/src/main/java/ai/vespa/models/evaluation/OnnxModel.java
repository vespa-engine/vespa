// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.io.File;
import java.util.Map;

/**
 * A named ONNX model that should be evaluated with OnnxEvaluator.
 *
 * @author lesters
 */
class OnnxModel {

    private final String name;
    private final File modelFile;

    private OnnxEvaluator evaluator;

    OnnxModel(String name, File modelFile) {
        this.name = name;
        this.modelFile = modelFile;
    }

    public String name() {
        return name;
    }

    public void load() {
        if (evaluator == null) {
            evaluator = new OnnxEvaluator(modelFile.getPath());
        }
    }

    public Map<String, TensorType> inputs() {
        return evaluator().getInputInfo();
    }

    public Map<String, TensorType> outputs() {
        return evaluator().getOutputInfo();
    }

    public Tensor evaluate(Map<String, Tensor> inputs, String output) {
        return evaluator().evaluate(inputs, output);
    }

    private OnnxEvaluator evaluator() {
        if (evaluator == null) {
            throw new IllegalStateException("ONNX model has not been loaded.");
        }
        return evaluator;
    }

}
