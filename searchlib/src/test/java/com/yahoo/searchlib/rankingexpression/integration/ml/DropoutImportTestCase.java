// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author lesters
 */
public class DropoutImportTestCase {

    @Test
    public void testDropoutImport() {
        TestableTensorFlowModel model = new TestableTensorFlowModel("test", "src/test/files/integration/tensorflow/dropout/saved");

        // Check required functions
        assertEquals(1, model.get().inputs().size());
        assertTrue(model.get().inputs().containsKey("X"));
        assertEquals(new TensorType.Builder().indexed("d0").indexed("d1", 784).build(),
                     model.get().inputs().get("X"));

        ImportedModel.Signature signature = model.get().signature("serving_default");

        assertEquals("Has skipped outputs",
                     0, model.get().signature("serving_default").skippedOutputs().size());

        ExpressionFunction output = signature.outputExpression("y");
        assertNotNull(output);
        assertEquals("outputs/Maximum", output.getBody().getName());
        assertEquals("join(join(imported_ml_function_test_outputs_BiasAdd, reduce(constant(test_outputs_Const), sum, d1), f(a,b)(a * b)), imported_ml_function_test_outputs_BiasAdd, f(a,b)(max(a,b)))",
                     output.getBody().getRoot().toString());
        model.assertEqualResult("X", output.getBody().getName());
        assertEquals("{x=tensor(d0[],d1[784])}", output.argumentTypes().toString());
    }

}
