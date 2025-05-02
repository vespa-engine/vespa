// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * @author lesters
 */
public class EmbeddedOnnxEvaluatorTest {

    @Test
    public void testSimpleModel() {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        var runtime = new EmbeddedOnnxRuntime();
        OnnxEvaluator evaluator = runtime.evaluatorOf("src/test/models/onnx/simple/simple.onnx");

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
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        var runtime = new EmbeddedOnnxRuntime();
        OnnxEvaluator evaluator = runtime.evaluatorOf("src/test/models/onnx/pytorch/one_layer.onnx");

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
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        var runtime = new EmbeddedOnnxRuntime();
        String expected = "tensor<float>(d0[2],d1[4]):[38,44,50,56,83,98,113,128]";
        String input1 = "tensor<float>(d0[2],d1[3]):[1,2,3,4,5,6]";
        String input2 = "tensor<float>(d0[3],d1[4]):[1,2,3,4,5,6,7,8,9,10,11,12]";
        assertEvaluate(runtime, "simple/matmul.onnx", expected, input1, input2);
    }

    @Test
    public void testTypes() {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        var runtime = new EmbeddedOnnxRuntime();
        assertEvaluate(runtime, "add_double.onnx", "tensor(d0[1]):[3]", "tensor(d0[1]):[1]", "tensor(d0[1]):[2]");
        assertEvaluate(runtime, "add_float.onnx", "tensor<float>(d0[1]):[3]", "tensor<float>(d0[1]):[1]", "tensor<float>(d0[1]):[2]");
        assertEvaluate(runtime, "add_float16.onnx", "tensor<float>(d0[1]):[3]", "tensor<float>(d0[1]):[1]", "tensor<float>(d0[1]):[2]");
        //Add is not a supported operation for bfloat16 types in onnx operators.
        assertEvaluate(runtime, "sign_bfloat16.onnx", "tensor<bfloat16>(d0[1]):[1]", "tensor<bfloat16>(d0[1]):[1]");

        assertEvaluate(runtime, "add_int64.onnx", "tensor<double>(d0[1]):[3]", "tensor<double>(d0[1]):[1]", "tensor<double>(d0[1]):[2]");
        assertEvaluate(runtime, "cast_int8_float.onnx", "tensor<float>(d0[1]):[-128]", "tensor<int8>(d0[1]):[128]");
        assertEvaluate(runtime, "cast_float_int8.onnx", "tensor<int8>(d0[1]):[-1]", "tensor<float>(d0[1]):[255]");
        assertEvaluate(runtime,"cast_bfloat16_float.onnx", "tensor<float>(d0[1]):[1]", "tensor<bfloat16>(d0[1]):[1]");
    }

    @Test
    public void testNotIdentifiers() {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        var runtime = new EmbeddedOnnxRuntime();
        OnnxEvaluator evaluator = runtime.evaluatorOf("src/test/models/onnx/badnames.onnx");
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

    @Test
    public void testLoadModelFromBytes() throws IOException {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        var runtime = new EmbeddedOnnxRuntime();
        var model = Files.readAllBytes(Paths.get("src/test/models/onnx/simple/simple.onnx"));
        var evaluator = runtime.evaluatorOf(model);
        assertEquals(3, evaluator.getInputs().size());
        assertEquals(1, evaluator.getOutputs().size());
        evaluator.close();
    }

    @Test
    public void testLoggingMessages() throws IOException {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        Logger logger = Logger.getLogger(EmbeddedOnnxEvaluator.class.getName());
        CustomLogHandler logHandler = new CustomLogHandler();
        logger.addHandler(logHandler);
        var runtime = new EmbeddedOnnxRuntime();
        var model = Files.readAllBytes(Paths.get("src/test/models/onnx/simple/simple.onnx"));
        OnnxEvaluatorOptions options = new OnnxEvaluatorOptions();
        options.setGpuDevice(0);
        var evaluator = runtime.evaluatorOf(model,options);
        evaluator.close();
        List<LogRecord> records = logHandler.getLogRecords();
        assertEquals(1,records.size());
        assertEquals(Level.INFO,records.get(0).getLevel());
        String message = records.get(0).getMessage();
        assertEquals("Failed to create session with CUDA using GPU device 0. " +
                "Falling back to CPU. Reason: Error code - ORT_EP_FAIL - message:" +
                " Failed to find CUDA shared provider", message);
        logger.removeHandler(logHandler);

    }

    private void assertEvaluate(OnnxRuntime runtime, String model, String output, String... input) {
        OnnxEvaluator evaluator = runtime.evaluatorOf("src/test/models/onnx/" + model);
        Map<String, Tensor> inputs = new HashMap<>();
        for (int i = 0; i < input.length; ++i) {
            inputs.put("input" + (i+1), Tensor.from(input[i]));
        }
        Tensor expected = Tensor.from(output);
        Tensor result = evaluator.evaluate(inputs, "output");
        assertEquals(expected, result);
        assertEquals(expected.type().valueType(), result.type().valueType());
    }

    static class CustomLogHandler extends Handler {
        private List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }

        public List<LogRecord> getLogRecords() {
            return records;
        }
    }

}
