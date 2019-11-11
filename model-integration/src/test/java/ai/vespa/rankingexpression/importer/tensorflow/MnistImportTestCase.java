// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.tensorflow;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlFunction;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import ai.vespa.rankingexpression.importer.ImportedModel;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author bratseth
 */
public class MnistImportTestCase {

    @Test
    public void testMnistImport() {
        TestableTensorFlowModel model = new TestableTensorFlowModel("test", "src/test/models/tensorflow/mnist/saved");
        ImportedModel.Signature signature = model.get().signature("serving_default");
        Assert.assertEquals("Should have no skipped outputs",
                            0, model.get().signature("serving_default").skippedOutputs().size());

        ImportedMlFunction output = signature.outputFunction("y", "y");
        assertNotNull(output);
        model.assertEqualResultSum("input", "dnn/outputs/add", 0.0001);
    }

}
