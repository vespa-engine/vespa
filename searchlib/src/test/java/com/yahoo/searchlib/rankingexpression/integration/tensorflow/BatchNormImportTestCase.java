// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import org.junit.Test;
import org.tensorflow.SavedModelBundle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author lesters
 */
public class BatchNormImportTestCase {

    @Test
    public void testBatchNormImport() {
        TensorFlowImportTester tester = new TensorFlowImportTester("src/test/files/integration/tensorflow/batch_norm/saved");
        TensorFlowModel.Signature signature = tester.result().signature("serving_default");

        assertEquals("Has skipped outputs",
                     0, tester.result().signature("serving_default").skippedOutputs().size());

        RankingExpression output = signature.outputExpression("y");
        assertNotNull(output);
        assertEquals("dnn/batch_normalization_3/batchnorm/add_1", output.getName());
        tester.assertEqualResult("X", output.getName());
    }

}
