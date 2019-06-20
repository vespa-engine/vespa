// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.tensorflow;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlFunction;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import ai.vespa.rankingexpression.importer.ImportedModel;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author lesters
 */
public class BatchNormImportTestCase {

    @Test
    public void testBatchNormImport() {
        TestableTensorFlowModel model = new TestableTensorFlowModel("test",
                                                                    "src/test/models/tensorflow/batch_norm/saved");
        ImportedModel.Signature signature = model.get().signature("serving_default");

        assertEquals("Should have no skipped outputs",
                     0, model.get().signature("serving_default").skippedOutputs().size());


        // Test signature
        ImportedMlFunction function = signature.outputFunction("y", "y");
        assertNotNull(function);
        assertEquals("{X=tensor(d0[],d1[784])}", function.argumentTypes().toString());

        // Test outputs
        List<ImportedMlFunction> outputs = model.get().outputExpressions();
        assertEquals(1, outputs.size());
        assertEquals("serving_default.y", outputs.get(0).name());
        assertEquals("{X=tensor(d0[],d1[784])}", function.argumentTypes().toString());
        model.assertEqualResult("X", "dnn/batch_normalization_3/batchnorm/add_1");
    }


}
