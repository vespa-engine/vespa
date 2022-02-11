// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

/**
 * @author lesters
 */
public class OnnxEvaluatorTest {

    @Test
    public void testSimpleModel() {
        assumeTrue(OnnxEvaluator.isRuntimeAvailable());
        OnnxEvaluator evaluator = new OnnxEvaluator("src/test/models/onnx/simple/simple.onnx");

        // Input types
        Map<String, TensorType> inputTypes = evaluator.getInputInfo();
        assertEquals(inputTypes.get("query_tensor"), TensorType.fromSpec("tensor<float>(d0[1],d1[4])"));
        assertEquals(inputTypes.get("attribute_tensor"), TensorType.fromSpec("tensor<float>(d0[4],d1[1])"));
        assertEquals(inputTypes.get("bias_tensor"), TensorType.fromSpec("tensor<float>(d0[1],d1[1])"));

        // Output types
        Map<String, TensorType> outputTypes = evaluator.getOutputInfo();
        assertEquals(outputTypes.get("output"), TensorType.fromSpec("tensor<float>(d0[1],d1[1])"));

        // Evaluation
        Map<String, Tensor> inputs = new HashMap<>();
        inputs.put("query_tensor", Tensor.from("tensor(d0[1],d1[4]):[0.1, 0.2, 0.3, 0.4]"));
        inputs.put("attribute_tensor", Tensor.from("tensor(d0[4],d1[1]):[0.1, 0.2, 0.3, 0.4]"));
        inputs.put("bias_tensor", Tensor.from("tensor(d0[1],d1[1]):[1.0]"));

        assertEquals(evaluator.evaluate(inputs).get("output"), Tensor.from("tensor(d0[1],d1[1]):[1.3]"));
        assertEquals(evaluator.evaluate(inputs, "output"), Tensor.from("tensor(d0[1],d1[1]):[1.3]"));
    }

    @Test
    public void testBatchDimension() {
        assumeTrue(OnnxEvaluator.isRuntimeAvailable());
        OnnxEvaluator evaluator = new OnnxEvaluator("src/test/models/onnx/pytorch/one_layer.onnx");

        // Input types
        Map<String, TensorType> inputTypes = evaluator.getInputInfo();
        assertEquals(inputTypes.get("input"), TensorType.fromSpec("tensor<float>(d0[],d1[3])"));

        // Output types
        Map<String, TensorType> outputTypes = evaluator.getOutputInfo();
        assertEquals(outputTypes.get("output"), TensorType.fromSpec("tensor<float>(d0[],d1[1])"));

        // Evaluation
        Map<String, Tensor> inputs = new HashMap<>();
        inputs.put("input", Tensor.from("tensor<float>(d0[2],d1[3]):[[0.1, 0.2, 0.3],[0.4,0.5,0.6]]"));
        assertEquals(evaluator.evaluate(inputs, "output"), Tensor.from("tensor<float>(d0[2],d1[1]):[0.6393113,0.67574286]"));
    }

    @Test
    public void testMatMul() {
        assumeTrue(OnnxEvaluator.isRuntimeAvailable());
        String expected = "tensor<float>(d0[2],d1[4]):[38,44,50,56,83,98,113,128]";
        String input1 = "tensor<float>(d0[2],d1[3]):[1,2,3,4,5,6]";
        String input2 = "tensor<float>(d0[3],d1[4]):[1,2,3,4,5,6,7,8,9,10,11,12]";
        assertEvaluate("simple/matmul.onnx", expected, input1, input2);
    }

    @Test
    public void testTypes() {
        assumeTrue(OnnxEvaluator.isRuntimeAvailable());
        assertEvaluate("add_double.onnx", "tensor(d0[1]):[3]", "tensor(d0[1]):[1]", "tensor(d0[1]):[2]");
        assertEvaluate("add_float.onnx", "tensor<float>(d0[1]):[3]", "tensor<float>(d0[1]):[1]", "tensor<float>(d0[1]):[2]");
        assertEvaluate("add_int64.onnx", "tensor<double>(d0[1]):[3]", "tensor<double>(d0[1]):[1]", "tensor<double>(d0[1]):[2]");
        assertEvaluate("cast_int8_float.onnx", "tensor<float>(d0[1]):[-128]", "tensor<int8>(d0[1]):[128]");
        assertEvaluate("cast_float_int8.onnx", "tensor<int8>(d0[1]):[-1]", "tensor<float>(d0[1]):[255]");

        // ONNX Runtime 1.8.0 does not support much of bfloat16 yet
        // assertEvaluate("cast_bfloat16_float.onnx", "tensor<float>(d0[1]):[1]", "tensor<bfloat16>(d0[1]):[1]");
    }

    private void assertEvaluate(String model, String output, String... input) {
        OnnxEvaluator evaluator = new OnnxEvaluator("src/test/models/onnx/" + model);
        Map<String, Tensor> inputs = new HashMap<>();
        for (int i = 0; i < input.length; ++i) {
            inputs.put("input" + (i+1), Tensor.from(input[i]));
        }
        Tensor expected = Tensor.from(output);
        Tensor result = evaluator.evaluate(inputs, "output");
        assertEquals(expected, result);
        assertEquals(expected.type().valueType(), result.type().valueType());
    }

}
