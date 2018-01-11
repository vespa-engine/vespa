package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
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
public class TensorflowImportTestCase {

    @Test
    public void testMnistSoftmaxImport() {
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

        // Test execution
        assertEqualResult(model, result, "Placeholder", "Variable/read");
        assertEqualResult(model, result, "Placeholder", "Variable_1/read");
        assertEqualResult(model, result, "Placeholder", "MatMul");
        assertEqualResult(model, result, "Placeholder", "add");
    }

    @Test
    public void test3LayerMnistImport() {
        String modelDir = "src/test/files/integration/tensorflow/3_layer_mnist/saved";
        SavedModelBundle model = SavedModelBundle.load(modelDir, "serve");
        TensorFlowModel result = new TensorFlowImporter().importModel(model);

        // Check constants
        assertEquals(8, result.constants().size());

        Tensor outputBias = result.constants().get("dnn/outputs/bias");
        assertNotNull(outputBias);
        assertEquals(new TensorType.Builder().indexed("d0", 10).build(), outputBias.type());
        assertEquals(10, outputBias.size());

        Tensor outputWeights = result.constants().get("dnn/outputs/weights");
        assertNotNull(outputWeights);
        assertEquals(new TensorType.Builder().indexed("d0", 40).indexed("d1", 10).build(), outputWeights.type());
        assertEquals(400, outputWeights.size());

        Tensor hidden3Bias = result.constants().get("dnn/hidden3/bias");
        assertNotNull(hidden3Bias);
        assertEquals(new TensorType.Builder().indexed("d0", 40).build(), hidden3Bias.type());
        assertEquals(40, hidden3Bias.size());

        Tensor hidden3Weights = result.constants().get("dnn/hidden3/weights");
        assertNotNull(hidden3Weights);
        assertEquals(new TensorType.Builder().indexed("d0", 100).indexed("d1", 40).build(), hidden3Weights.type());
        assertEquals(4000, hidden3Weights.size());

        Tensor hidden2Bias = result.constants().get("dnn/hidden2/bias");
        assertNotNull(hidden2Bias);
        assertEquals(new TensorType.Builder().indexed("d0", 100).build(), hidden2Bias.type());
        assertEquals(100, hidden2Bias.size());

        Tensor hidden2Weights = result.constants().get("dnn/hidden2/weights");
        assertNotNull(hidden2Weights);
        assertEquals(new TensorType.Builder().indexed("d0", 300).indexed("d1", 100).build(), hidden2Weights.type());
        assertEquals(30000, hidden2Weights.size());

        Tensor hidden1Bias = result.constants().get("dnn/hidden1/bias");
        assertNotNull(hidden1Bias);
        assertEquals(new TensorType.Builder().indexed("d0", 300).build(), hidden1Bias.type());
        assertEquals(300, hidden1Bias.size());

        Tensor hidden1Weights = result.constants().get("dnn/hidden1/weights");
        assertNotNull(hidden1Weights);
        assertEquals(new TensorType.Builder().indexed("d0", 784).indexed("d1", 300).build(), hidden1Weights.type());
        assertEquals(235200, hidden1Weights.size());

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
        assertEquals("dnn/outputs/add", output.getName());
        assertEquals("" +
                    "join(" +
                        "rename(" +
                            "matmul(" +
                                "map(" +
                                    "join(" +
                                        "rename(" +
                                            "matmul(" +
                                                "map(" +
                                                    "join(" +
                                                        "rename(" +
                                                            "matmul(" +
                                                                "map(" +
                                                                    "join(" +
                                                                        "rename(" +
                                                                            "matmul(" +
                                                                                "X, " +
                                                                                "rename(" +
                                                                                    "constant(dnn/hidden1/weights), " +
                                                                                    "(d0, d1), " +
                                                                                    "(d1, d3)" +
                                                                                "), " +
                                                                                "d1" +
                                                                            "), " +
                                                                            "d3, " +
                                                                            "d1" +
                                                                        "), " +
                                                                        "rename(" +
                                                                            "constant(dnn/hidden1/bias), " +
                                                                            "d0, " +
                                                                            "d1" +
                                                                        "), " +
                                                                        "f(a,b)(a + b)" +
                                                                    "), " +
                                                                    "f(a)(if(a < 0, exp(a)-1, a))" +
                                                                "), " +
                                                                "rename(" +
                                                                    "constant(dnn/hidden2/weights), " +
                                                                    "(d0, d1), " +
                                                                    "(d1, d3)" +
                                                                "), " +
                                                                "d1" +
                                                            "), " +
                                                            "d3, " +
                                                            "d1" +
                                                        "), " +
                                                        "rename(" +
                                                            "constant(dnn/hidden2/bias), " +
                                                            "d0, " +
                                                            "d1" +
                                                        "), " +
                                                        "f(a,b)(a + b)" +
                                                    "), " +
                                                    "f(a)(max(0, a))" +
                                                "), " +
                                                "rename(" +
                                                    "constant(dnn/hidden3/weights), " +
                                                    "(d0, d1), " +
                                                    "(d1, d3)" +
                                                "), " +
                                                "d1" +
                                            "), " +
                                            "d3, " +
                                            "d1" +
                                        "), " +
                                        "rename(" +
                                            "constant(dnn/hidden3/bias), " +
                                                "d0, " +
                                                "d1" +
                                            "), " +
                                        "f(a,b)(a + b)" +
                                    "), " +
                                    "f(a)(1 / (1 + exp(-a)))" +
                                "), " +
                                "rename(" +
                                    "constant(dnn/outputs/weights), " +
                                    "(d0, d1), " +
                                    "(d1, d3)" +
                                "), " +
                                "d1" +
                            "), " +
                            "d3, " +
                            "d1" +
                        "), " +
                        "rename(" +
                            "constant(dnn/outputs/bias), " +
                            "d0, " +
                            "d1" +
                        "), " +
                        "f(a,b)(a + b)" +
                    ")",
                toNonPrimitiveString(output));

        // Test constants
        assertEqualResult(model, result, "X", "dnn/hidden1/weights/read");
        assertEqualResult(model, result, "X", "dnn/hidden1/bias/read");
        assertEqualResult(model, result, "X", "dnn/hidden2/weights/read");
        assertEqualResult(model, result, "X", "dnn/hidden2/bias/read");
        assertEqualResult(model, result, "X", "dnn/hidden3/weights/read");
        assertEqualResult(model, result, "X", "dnn/hidden3/bias/read");
        assertEqualResult(model, result, "X", "dnn/outputs/weights/read");
        assertEqualResult(model, result, "X", "dnn/outputs/bias/read");

        // Test execution
        assertEqualResult(model, result, "X", "dnn/hidden1/MatMul");
        assertEqualResult(model, result, "X", "dnn/hidden1/add");
        assertEqualResult(model, result, "X", "dnn/hidden1/Elu");
        assertEqualResult(model, result, "X", "dnn/hidden2/MatMul");
        assertEqualResult(model, result, "X", "dnn/hidden2/add");
        assertEqualResult(model, result, "X", "dnn/hidden2/Relu");
        assertEqualResult(model, result, "X", "dnn/hidden3/MatMul");
        assertEqualResult(model, result, "X", "dnn/hidden3/add");
        assertEqualResult(model, result, "X", "dnn/hidden3/Sigmoid");
        assertEqualResult(model, result, "X", "dnn/outputs/MatMul");
        assertEqualResult(model, result, "X", "dnn/outputs/add");
    }


    private void assertEqualResult(SavedModelBundle model, TensorFlowModel result, String inputName, String operationName) {
        Tensor tfResult = tensorFlowExecute(model, inputName, operationName);
        Context context = contextFrom(result);
        Tensor placeholder = placeholderArgument();
        context.put(inputName, new TensorValue(placeholder));
        Tensor vespaResult = result.expressions().get(operationName).evaluate(context).asTensor();
        assertEquals("Operation '" + operationName + "' produces equal results", vespaResult, tfResult);
    }

    private Tensor tensorFlowExecute(SavedModelBundle model, String inputName, String operationName) {
        Session.Runner runner = model.session().runner();
        org.tensorflow.Tensor<?> placeholder = org.tensorflow.Tensor.create(new long[]{ 1, 784 }, FloatBuffer.allocate(784));
        runner.feed(inputName, placeholder);
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
