// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.ml;

import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.models.evaluation.FunctionEvaluator;
import ai.vespa.models.evaluation.ModelsEvaluator;
import com.yahoo.tensor.Tensor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests the ModelsEvaluatorTester.
 *
 * @author lesters
 */
public class ModelsEvaluatorTest {

    @Test
    void testModelsEvaluator() {
        // Assumption fails but test passes on Intel macs
        // Assumption fails and test fails on ARM64
        assumeTrue(OnnxRuntime.isRuntimeAvailable());

        ModelsEvaluator modelsEvaluator = ModelsEvaluatorTester.create("src/test/cfg/application/stateless_eval");
        assertEquals(3, modelsEvaluator.models().size());

        // ONNX model evaluation
        FunctionEvaluator mul = modelsEvaluator.evaluatorOf("mul");
        Tensor input1 = Tensor.from("tensor<float>(d0[1]):[2]");
        Tensor input2 = Tensor.from("tensor<float>(d0[1]):[3]");
        Tensor output = mul.bind("input1", input1).bind("input2", input2).evaluate();
        assertEquals(6.0, output.sum().asDouble(), 1e-9);

        // LightGBM model evaluation
        FunctionEvaluator lgbm = modelsEvaluator.evaluatorOf("lightgbm_regression");
        lgbm.bind("numerical_1", 0.1).bind("numerical_2", 0.2).bind("categorical_1", "a").bind("categorical_2", "i");
        output = lgbm.evaluate();
        assertEquals(2.0547, output.sum().asDouble(), 1e-4);

        // Vespa model evaluation
        FunctionEvaluator foo1 = modelsEvaluator.evaluatorOf("example", "foo1");
        input1 = Tensor.from("tensor(name{},x[3]):{{name:n,x:0}:1,{name:n,x:1}:2,{name:n,x:2}:3 }");
        input2 = Tensor.from("tensor(x[3]):[2,3,4]");
        output = foo1.bind("input1", input1).bind("input2", input2).evaluate();
        assertEquals(90, output.asDouble(), 1e-9);

        FunctionEvaluator foo2 = modelsEvaluator.evaluatorOf("example", "foo2");
        input1 = Tensor.from("tensor(name{},x[3]):{{name:n,x:0}:1,{name:n,x:1}:2,{name:n,x:2}:3 }");
        input2 = Tensor.from("tensor(x[3]):[2,3,4]");
        output = foo2.bind("input1", input1).bind("input2", input2).evaluate();
        assertEquals(90, output.asDouble(), 1e-9);
    }

}
