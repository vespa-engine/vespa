// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.tensorflow;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.ImportedModel;
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

        Assert.assertEquals("Has skipped outputs",
                            0, model.get().signature("serving_default").skippedOutputs().size());

        ExpressionFunction output = signature.outputExpression("y");
        assertNotNull(output);
        assertEquals("dnn/outputs/add", output.getBody().getName());
        model.assertEqualResultSum("input", output.getBody().getName(), 0.00001);
    }

}
