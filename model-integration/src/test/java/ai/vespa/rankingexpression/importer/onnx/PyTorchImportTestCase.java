// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.ImportedModel;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author lesters
 */
public class PyTorchImportTestCase extends TestableModel {

    @Test
    public void testPyTorchExport() {
        ImportedModel model = new OnnxImporter().importModel("test", "src/test/models/pytorch/pytorch.onnx").asNative();
        Tensor onnxResult = evaluateVespa(model, "output", model.inputs());
        assertEquals(Tensor.from("tensor(d0[1],d1[2]):[[0.28258783057229725, -0.0685615853647904]]"), onnxResult);
    }

}
