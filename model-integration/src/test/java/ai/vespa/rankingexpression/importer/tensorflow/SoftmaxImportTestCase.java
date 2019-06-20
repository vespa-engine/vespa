// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.tensorflow;

import ai.vespa.rankingexpression.importer.ImportedModel;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlFunction;
import org.junit.Assert;
import org.junit.Test;


import static org.junit.Assert.assertNotNull;

/**
 * @author lesters
 */
public class SoftmaxImportTestCase {

    @Test
    public void testSoftmaxImport() {
        TestableTensorFlowModel model = new TestableTensorFlowModel("test", "src/test/models/tensorflow/softmax/saved", 1, 5);
        ImportedModel.Signature signature = model.get().signature("serving_default");
        Assert.assertEquals("Should have no skipped outputs",
                0, model.get().signature("serving_default").skippedOutputs().size());

        ImportedMlFunction output = signature.outputFunction("y", "y");
        assertNotNull(output);
        model.assertEqualResult("input", "output");
    }

}
