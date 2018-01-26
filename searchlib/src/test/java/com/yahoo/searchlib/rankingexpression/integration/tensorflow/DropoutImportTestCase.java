// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author lesters
 */
public class DropoutImportTestCase {

    @Test
    public void testDropoutImport() {
        TestableTensorFlowModel model = new TestableTensorFlowModel("src/test/files/integration/tensorflow/dropout/saved");
        TensorFlowModel.Signature signature = model.get().signature("serving_default");

        assertEquals("Has skipped outputs",
                     0, model.get().signature("serving_default").skippedOutputs().size());

        RankingExpression output = signature.outputExpression("y");
        assertNotNull(output);
        assertEquals("outputs/BiasAdd", output.getName());
        assertEquals("join(rename(reduce(join(X, rename(constant(\"outputs/kernel\"), (d0, d1), (d1, d3)), f(a,b)(a * b)), sum, d1), d3, d1), rename(constant(\"outputs/bias\"), d0, d1), f(a,b)(a + b))",
                output.getRoot().toString());
        model.assertEqualResult("X", output.getName());
    }

}
