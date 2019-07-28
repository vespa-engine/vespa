// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.tensorflow;

import ai.vespa.rankingexpression.importer.ImportedModel;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author bratseth
 */
public class RegressionTestCase {

    @Test
    public void testRegressionModel1() {
        TestableTensorFlowModel model = new TestableTensorFlowModel("test",
                                                                    "src/test/models/tensorflow/regression/test1",
                                                                    14,
                                                                    1536);

        // Check constants
        Assert.assertEquals(2, model.get().largeConstants().size());

        Tensor constant0 = Tensor.from(model.get().largeConstants().get("test_Variable_read"));
        assertNotNull(constant0);
        assertEquals(new TensorType.Builder().indexed("d2", 1536).indexed("d1", 14).build(),
                     constant0.type());
        assertEquals(21504, constant0.size());

        Tensor constant1 = Tensor.from(model.get().largeConstants().get("test_Variable_1_read"));
        assertNotNull(constant1);
        assertEquals(new TensorType.Builder().indexed("d1", 14).build(), constant1.type());
        assertEquals(14, constant1.size());

        // Check (provided) functions
        Assert.assertEquals(0, model.get().functions().size());

        // Check signatures
        Assert.assertEquals(1, model.get().signatures().size());
        ImportedModel.Signature signature = model.get().signatures().get("serving_default");
        assertNotNull(signature);

        // Test execution
        model.assertEqualResult("input", "MatMul");
        model.assertEqualResult("input", "logits");
        model.assertEqualResult("input", "Sigmoid");
        model.assertEqualResult("input", "add");
    }

    @Test
    public void testRegressionModel2() {
        TestableTensorFlowModel model = new TestableTensorFlowModel("test",
                                                                    "src/test/models/tensorflow/regression/test2",
                                                                    14,
                                                                    1536,
                                                                    false);

        // Check constants
        Assert.assertEquals(2, model.get().largeConstants().size());

        // Check (provided) functions
        Assert.assertEquals(0, model.get().functions().size());

        // Check signatures
        Assert.assertEquals(1, model.get().signatures().size());
        ImportedModel.Signature signature = model.get().signatures().get("serving_default");
        assertNotNull(signature);

        // Test execution
        model.assertEqualResult("input", "MatMul");
        model.assertEqualResult("input", "add");
        model.assertEqualResult("input", "predict");
    }

}
