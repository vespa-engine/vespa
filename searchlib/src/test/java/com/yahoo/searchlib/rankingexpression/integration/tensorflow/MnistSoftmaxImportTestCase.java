// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class MnistSoftmaxImportTestCase {

    @Test
    public void testMnistSoftmaxImport() {
        TestableTensorFlowModel model = new TestableTensorFlowModel("src/test/files/integration/tensorflow/mnist_softmax/saved");

        // Check constants
        assertEquals(2, model.get().largeConstants().size());

        Tensor constant0 = model.get().largeConstants().get("Variable");
        assertNotNull(constant0);
        assertEquals(new TensorType.Builder().indexed("d0", 784).indexed("d1", 10).build(),
                     constant0.type());
        assertEquals(7840, constant0.size());

        Tensor constant1 = model.get().largeConstants().get("Variable_1");
        assertNotNull(constant1);
        assertEquals(new TensorType.Builder().indexed("d0", 10).build(),
                     constant1.type());
        assertEquals(10, constant1.size());

        // Check (provided) macros
        assertEquals(0, model.get().macros().size());

        // Check required macros
        assertEquals(1, model.get().requiredMacros().size());
        assertTrue(model.get().requiredMacros().containsKey("Placeholder"));
        assertEquals(new TensorType.Builder().indexed("d0").indexed("d1", 784).build(),
                     model.get().requiredMacros().get("Placeholder"));

        // Check signatures
        assertEquals(1, model.get().signatures().size());
        TensorFlowModel.Signature signature = model.get().signatures().get("serving_default");
        assertNotNull(signature);

        // ... signature inputs
        assertEquals(1, signature.inputs().size());
        TensorType argument0 = signature.inputArgument("x");
        assertNotNull(argument0);
        assertEquals(new TensorType.Builder().indexed("d0").indexed("d1", 784).build(), argument0);

        // ... signature outputs
        assertEquals(1, signature.outputs().size());
        RankingExpression output = signature.outputExpression("y");
        assertNotNull(output);
        assertEquals("add", output.getName());
        assertEquals("join(rename(reduce(join(Placeholder, rename(constant(\"Variable\"), (d0, d1), (d1, d3)), f(a,b)(a * b)), sum, d1), d3, d1), rename(constant(\"Variable_1\"), d0, d1), f(a,b)(a + b))",
                     output.getRoot().toString());

        // Test execution
        model.assertEqualResult("Placeholder", "Variable/read");
        model.assertEqualResult("Placeholder", "Variable_1/read");
        model.assertEqualResult("Placeholder", "MatMul");
        model.assertEqualResult("Placeholder", "add");
    }

}
