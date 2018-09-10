// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author bratseth
 */
public class MnistImportTestCase {

    @Test
    public void testMnistImport() {
        TestableTensorFlowModel model = new TestableTensorFlowModel("test", "src/test/files/integration/tensorflow/mnist/saved");
        ImportedModel.Signature signature = model.get().signature("serving_default");

        assertEquals("Has skipped outputs",
                     0, model.get().signature("serving_default").skippedOutputs().size());

        RankingExpression output = signature.outputExpression("y");
        assertNotNull(output);
        assertEquals("dnn/outputs/add", output.getName());
        model.assertEqualResultSum("input", output.getName(), 0.00001);
    }


}
