// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlFunction;
import ai.vespa.rankingexpression.importer.ImportedModel;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author lesters
 */
public class OnnxMnistSoftmaxImportTestCase {

    @Test
    public void testMnistSoftmaxImport() {
        ImportedModel model = new OnnxImporter().importModel("test", "src/test/models/onnx/mnist_softmax/mnist_softmax.onnx").asNative();

        // Check constants
        assertEquals(2, model.largeConstantTensors().size());

        Tensor constant0 = model.largeConstantTensors().get("test_Variable");
        assertNotNull(constant0);
        assertEquals(new TensorType.Builder(TensorType.Value.FLOAT).indexed("d2", 784).indexed("d1", 10).build(),
                     constant0.type());
        assertEquals(7840, constant0.size());

        Tensor constant1 = model.largeConstantTensors().get("test_Variable_1");
        assertNotNull(constant1);
        assertEquals(new TensorType.Builder(TensorType.Value.FLOAT).indexed("d1", 10).build(), constant1.type());
        assertEquals(10, constant1.size());

        // Check inputs
        assertEquals(1, model.inputs().size());
        assertTrue(model.inputs().containsKey("Placeholder"));
        assertEquals(TensorType.fromSpec("tensor<float>(d0[],d1[784])"), model.inputs().get("Placeholder"));

        // Check signature
        ImportedMlFunction output = model.defaultSignature().outputFunction("add", "add");
        assertNotNull(output);
        assertEquals("join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), constant(test_Variable), f(a,b)(a * b)), sum, d2), constant(test_Variable_1), f(a,b)(a + b))",
                     output.expression());
        assertEquals(TensorType.fromSpec("tensor<float>(d0[],d1[784])"),
                     model.inputs().get(model.defaultSignature().inputs().get("Placeholder")));
        assertEquals("{Placeholder=tensor<float>(d0[],d1[784])}", output.argumentTypes().toString());
    }

}
