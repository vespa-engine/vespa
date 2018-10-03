// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author lesters
 */
public class BatchNormImportTestCase {

    @Test
    public void testBatchNormImport() {
        TestableTensorFlowModel model = new TestableTensorFlowModel("test",
                                                                    "src/test/files/integration/tensorflow/batch_norm/saved");
        ImportedModel.Signature signature = model.get().signature("serving_default");

        assertEquals("Has skipped outputs",
                     0, model.get().signature("serving_default").skippedOutputs().size());

        ExpressionFunction function = signature.outputExpression("y");
        assertNotNull(function);
        assertEquals("dnn/batch_normalization_3/batchnorm/add_1", function.getBody().getName());
        model.assertEqualResult("X", function.getBody().getName());
        assertEquals("{X=tensor(d0[],d1[784])}", function.argumentTypes().toString());
    }

}
