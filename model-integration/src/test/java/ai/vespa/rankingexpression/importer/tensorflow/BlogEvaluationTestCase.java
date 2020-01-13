// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.tensorflow;

import ai.vespa.rankingexpression.importer.ImportedModel;
import org.junit.Test;
import org.tensorflow.SavedModelBundle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author bratseth
 */
public class BlogEvaluationTestCase {

    static final String modelDir = "src/test/models/tensorflow/blog/saved";

    @Test
    public void testImport() {
        SavedModelBundle tensorFlowModel = SavedModelBundle.load(modelDir, "serve");
        ImportedModel model = new TensorFlowImporter().importModel("blog", modelDir, tensorFlowModel);

        ImportedModel.Signature y = model.signature("serving_default.y");
        assertNotNull(y);
        assertEquals(0, y.inputs().size());
    }

}
