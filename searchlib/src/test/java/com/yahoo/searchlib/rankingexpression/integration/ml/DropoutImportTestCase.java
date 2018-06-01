// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml;

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

        // Check required macros
        assertEquals(1, model.get().requiredMacros().size());
        assertTrue(model.get().requiredMacros().containsKey("X"));
        assertEquals(new TensorType.Builder().indexed("d0").indexed("d1", 784).build(),
                     model.get().requiredMacros().get("X"));

        ImportedModel.Signature signature = model.get().signature("serving_default");

        assertEquals("Has skipped outputs",
                     0, model.get().signature("serving_default").skippedOutputs().size());

        RankingExpression output = signature.outputExpression("y");
        assertNotNull(output);
        assertEquals("outputs/Maximum", output.getName());
        assertEquals("join(join(imported_ml_macro_test_outputs_BiasAdd, reduce(constant(test_outputs_Const), sum, d1), f(a,b)(a * b)), imported_ml_macro_test_outputs_BiasAdd, f(a,b)(max(a,b)))",
                output.getRoot().toString());
        model.assertEqualResult("X", output.getName());
    }

}
