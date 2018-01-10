package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Ignore;
import org.junit.Test;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;

import java.nio.FloatBuffer;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author bratseth
 */
public class Mnist_SoftmaxTestCase {

    @Ignore
    @Test
    public void testImporting() {
        String modelDir = "src/test/files/integration/tensorflow/mnist_softmax/saved";
        SavedModelBundle model = SavedModelBundle.load(modelDir, "serve");
        TensorFlowModel result = new TensorFlowImporter().importModel(model);

        // Check constants
        assertEquals(2, result.constants().size());

        Tensor constant0 = result.constants().get("Variable");
        assertNotNull(constant0);
        assertEquals(new TensorType.Builder().indexed("d0", 784).indexed("d1", 10).build(),
                     constant0.type());
        assertEquals(7840, constant0.size());

        Tensor constant1 = result.constants().get("Variable_1");
        assertNotNull(constant1);
        assertEquals(new TensorType.Builder().indexed("d0", 10).build(),
                     constant1.type());
        assertEquals(10, constant1.size());

        // Check signatures
        assertEquals(1, result.signatures().size());
        TensorFlowModel.Signature signature = result.signatures().get("serving_default");
        assertNotNull(signature);

        // ... signature inputs
        assertEquals(1, signature.inputs().size());
        TensorType argument0 = signature.inputArgument("x");
        assertNotNull(argument0);
        assertEquals(new TensorType.Builder().indexed("d0").indexed("d1", 784).build(), argument0);

        // ... signature outputs
        assertEquals(1, signature.outputs().size());
        RankingExpression output = signature.outputExpression("y");
        assertNotNull(output);
        assertEquals("add", output.getName());
        assertEquals("" +
                     "join(rename(matmul(Placeholder, rename(constant(Variable), (d0, d1), (d1, d3)), d1), d3, d1), " +
                     "rename(constant(Variable_1), d0, d1), " +
                     "f(a,b)(a + b))",
                     toNonPrimitiveString(output));

        // ... skipped outputs
        assertEquals(0, signature.skippedOutputs().size());

        // Test execution
        assertEqualResult(model, result, "Variable/read");
        assertEqualResult(model, result, "Variable_1/read");
        assertEqualResult(model, result, "MatMul");
        assertEqualResult(model, result, "add");
    }

    private void assertEqualResult(SavedModelBundle model, TensorFlowModel result, String operationName) {
        Tensor tfResult = tensorFlowExecute(model, operationName);
        Context context = contextFrom(result);
        Tensor placeholder = placeholderArgument();
        context.put("Placeholder", new TensorValue(placeholder));
        Tensor vespaResult = result.expressions().get(operationName).evaluate(context).asTensor();
        assertEquals("Operation '" + operationName + "' produces equal results", vespaResult, tfResult);
    }

    private Tensor tensorFlowExecute(SavedModelBundle model, String operationName) {
        Session.Runner runner = model.session().runner();
        org.tensorflow.Tensor<?> placeholder = org.tensorflow.Tensor.create(new long[]{ 1, 784 }, FloatBuffer.allocate(784));
        runner.feed("Placeholder", placeholder);
        List<org.tensorflow.Tensor<?>> results = runner.fetch(operationName).run();
        assertEquals(1, results.size());
        return new TensorConverter().toVespaTensor(results.get(0));
    }

    private Context contextFrom(TensorFlowModel result) {
        MapContext context = new MapContext();
        result.constants().forEach((name, tensor) -> context.put("constant(" + name + ")", new TensorValue(tensor)));
        return context;
    }

    private String toNonPrimitiveString(RankingExpression expression) {
        // toString on the wrapping expression will map to primitives, which is harder to read
        return ((TensorFunctionNode)expression.getRoot()).function().toString();
    }

    private Tensor placeholderArgument() {
        int size = 784;
        Tensor.Builder b = Tensor.Builder.of(new TensorType.Builder().indexed("d0", 1).indexed("d1", size).build());
        for (int i = 0; i < size; i++)
            b.cell(0, 0, i);
        return b.build();
    }

}
