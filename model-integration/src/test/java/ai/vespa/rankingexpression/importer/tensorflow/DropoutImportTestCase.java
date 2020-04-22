// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.tensorflow;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlFunction;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import ai.vespa.rankingexpression.importer.ImportedModel;
import com.yahoo.tensor.TensorType;
import org.junit.Assert;
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
        TestableTensorFlowModel model = new TestableTensorFlowModel("test", "src/test/models/tensorflow/dropout/saved");

        // Check required functions
        Assert.assertEquals(1, model.get().inputs().size());
        assertTrue(model.get().inputs().containsKey("X"));
        Assert.assertEquals(new TensorType.Builder().indexed("d0").indexed("d1", 784).build(),
                            model.get().inputs().get("X"));

        ImportedModel.Signature signature = model.get().signature("serving_default");

        Assert.assertEquals("Should have no skipped outputs",
                            0, model.get().signature("serving_default").skippedOutputs().size());

        ImportedMlFunction function = signature.outputFunction("y", "y");
        assertNotNull(function);
        assertEquals("join(join(reduce(constant(test_outputs_Const), sum, d1), imported_ml_function_test_outputs_BiasAdd, f(a,b)(a * b)), imported_ml_function_test_outputs_BiasAdd, f(a,b)(max(a,b)))",
                     function.expression());
        model.assertEqualResult("X", "outputs/Maximum");
        assertEquals("{X=tensor(d0[],d1[784])}", function.argumentTypes().toString());
    }

}
