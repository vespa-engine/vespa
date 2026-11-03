// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * @author glebashnik
 */
public class TensorConverterTest {

    private static final String BADNAMES_MODEL = "src/test/models/onnx/badnames.onnx";

    @Test
    public void modelInputsResolveBothOnnxNameAndVespaIdentifier() throws OrtException {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        OrtSession session = sessionOf(BADNAMES_MODEL);
        var inputInfo = session.getInputInfo();
        var modelInputs = TensorConverter.ModelInputs.of(inputInfo);

        assertEquals("second/input:0", modelInputs.toOnnxName("second/input:0"));
        assertEquals("second/input:0", modelInputs.toOnnxName("second_input_0"));
        for (String onnxName : inputInfo.keySet()) {
            assertEquals(onnxName, modelInputs.toOnnxName(onnxName));
            assertEquals(onnxName, modelInputs.toOnnxName(TensorConverter.asValidName(onnxName)));
            assertEquals(inputInfo.get(onnxName).getInfo(), modelInputs.tensorInfo(onnxName));
        }

        var e = assertThrows(IllegalArgumentException.class, () -> modelInputs.toOnnxName("no_such_input"));
        assertEquals("ONNX model has no input with name no_such_input", e.getMessage());
    }

    @Test
    public void toOnnxTensorsFailsOnUnknownInputName() throws OrtException {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        OrtSession session = sessionOf(BADNAMES_MODEL);
        var modelInputs = TensorConverter.ModelInputs.of(session.getInputInfo());
        Map<String, Tensor> inputs = new LinkedHashMap<>();
        inputs.put("first_input", Tensor.from("tensor(d0[2]):[2,3]"));
        inputs.put("no_such_input", Tensor.from("tensor(d0[2]):[4,5]"));

        var e = assertThrows(IllegalArgumentException.class,
                             () -> TensorConverter.toOnnxTensors(inputs, modelInputs, OrtEnvironment.getEnvironment()));
        assertEquals("ONNX model has no input with name no_such_input", e.getMessage());
    }

    @Test
    public void toOnnxTensorsFailsOnUnsupportedTensor() throws OrtException {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        OrtSession session = sessionOf(BADNAMES_MODEL);
        var modelInputs = TensorConverter.ModelInputs.of(session.getInputInfo());
        Map<String, Tensor> inputs = new LinkedHashMap<>();
        inputs.put("first_input", Tensor.from("tensor(d0[2]):[2,3]"));
        inputs.put("second_input_0", Tensor.from("tensor(x{}):{a:1}"));

        var e = assertThrows(IllegalArgumentException.class,
                             () -> TensorConverter.toOnnxTensors(inputs, modelInputs, OrtEnvironment.getEnvironment()));
        assertTrue(e.getMessage().contains("only supports tensors with indexed dimensions"));
    }

    @Test
    public void toOnnxTensorsKeysResultByOnnxName() throws OrtException {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        OrtSession session = sessionOf(BADNAMES_MODEL);
        var modelInputs = TensorConverter.ModelInputs.of(session.getInputInfo());
        Map<String, Tensor> inputs = new LinkedHashMap<>();
        inputs.put("first_input", Tensor.from("tensor(d0[2]):[2,3]"));
        inputs.put("second_input_0", Tensor.from("tensor(d0[2]):[4,5]"));
        inputs.put("third_input", Tensor.from("tensor(d0[2]):[6,7]"));

        Map<String, OnnxTensor> result = TensorConverter.toOnnxTensors(inputs, modelInputs, OrtEnvironment.getEnvironment());
        try {
            assertEquals(session.getInputInfo().keySet(), result.keySet());
            assertTrue(result.containsKey("second/input:0"));
        } finally {
            result.values().forEach(OnnxTensor::close);
        }
    }

    private static OrtSession sessionOf(String modelPath) {
        var evaluator = (EmbeddedOnnxEvaluator) EmbeddedOnnxRuntime.createTestInstance().evaluatorOf(modelPath);
        return evaluator.ortSession();
    }

}
