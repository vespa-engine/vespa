// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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

    @Test
    public void testNotIdentifiers() {
        assumeTrue(OnnxEvaluator.isRuntimeAvailable());
        OnnxEvaluator evaluator = new OnnxEvaluator("src/test/models/onnx/badnames.onnx");
        var inputInfo = evaluator.getInputInfo();
        var outputInfo = evaluator.getOutputInfo();
        for (var entry : inputInfo.entrySet()) {
            System.out.println("wants input: " + entry.getKey() + " with type " + entry.getValue());
        }
        for (var entry : outputInfo.entrySet()) {
            System.out.println("will produce output: " + entry.getKey() + " with type " + entry.getValue());
        }

        assertEquals(3, inputInfo.size());
        assertTrue(inputInfo.containsKey("first_input"));
        assertTrue(inputInfo.containsKey("second_input_0"));
        assertTrue(inputInfo.containsKey("third_input"));

        assertEquals(3, outputInfo.size());
        assertTrue(outputInfo.containsKey("path_to_output_0"));
        assertTrue(outputInfo.containsKey("path_to_output_1"));
        assertTrue(outputInfo.containsKey("path_to_output_2"));

        Map<String, Tensor> inputs = new HashMap<>();
        inputs.put("first_input", Tensor.from("tensor(d0[2]):[2,3]"));
        inputs.put("second_input_0", Tensor.from("tensor(d0[2]):[4,5]"));
        inputs.put("third_input", Tensor.from("tensor(d0[2]):[6,7]"));

        Tensor result;
        result = evaluator.evaluate(inputs, "path_to_output_0");
        System.out.println("got result: " + result);
        assertTrue(result != null);

        result = evaluator.evaluate(inputs, "path_to_output_1");
        System.out.println("got result: " + result);
        assertTrue(result != null);

        result = evaluator.evaluate(inputs, "path_to_output_2");
        System.out.println("got result: " + result);
        assertTrue(result != null);

        var allResults = evaluator.evaluate(inputs);
        assertTrue(allResults != null);
        for (var entry : allResults.entrySet()) {
            System.out.println("produced output: " + entry.getKey() + " with type " + entry.getValue());
        }
        assertEquals(3, allResults.size());
        assertTrue(allResults.containsKey("path_to_output_0"));
        assertTrue(allResults.containsKey("path_to_output_1"));
        assertTrue(allResults.containsKey("path_to_output_2"));

        // we can also get output by onnx-internal name
        result = evaluator.evaluate(inputs, "path/to/output:0");
        System.out.println("got result: " + result);
        assertTrue(result != null);

        // we can also send input by onnx-internal name
        inputs.remove("second_input_0");
        inputs.put("second/input:0", Tensor.from("tensor(d0[2]):[8,9]"));
        allResults = evaluator.evaluate(inputs);
        assertTrue(allResults != null);
        for (var entry : allResults.entrySet()) {
            System.out.println("produced output: " + entry.getKey() + " with type " + entry.getValue());
        }
        assertEquals(3, allResults.size());
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
