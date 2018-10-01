// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Tests instantiating models from rank-profiles configs.
 *
 * @author bratseth
 */
public class MlModelsImportingTest {

    private static final double delta = 0.00000000001;

    @Test
    public void testImportingModels() {
        ModelTester tester = new ModelTester("src/test/resources/config/models/");

        assertEquals(4, tester.models().size());

        // TODO: When we get type information in Models, replace the evaluator.context().names() check below by that
        {
            Model xgboost = tester.models().get("xgboost_2_2");
            tester.assertFunction("xgboost_2_2",
                                  "(optimized sum of condition trees of size 192 bytes)",
                                  xgboost);


            // Function
            assertEquals(1, xgboost.functions().size());
            ExpressionFunction function = xgboost.functions().get(0);
            assertEquals("xgboost_2_2", function.getName());
            // assertEquals("f109, f29, f56, f60", commaSeparated(xgboost.functions().get(0).arguments())); TODO

            // Evaluator
            FunctionEvaluator evaluator = xgboost.evaluatorOf();
            assertEquals("f109, f29, f56, f60", evaluator.context().names().stream().sorted().collect(Collectors.joining(", ")));
            assertEquals(-8.17695, evaluator.evaluate().sum().asDouble(), delta);
        }

        {

            Model onnxMnistSoftmax = tester.models().get("mnist_softmax");
            tester.assertFunction("default.add",
                                  "join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), constant(mnist_softmax_Variable), f(a,b)(a * b)), sum, d2), constant(mnist_softmax_Variable_1), f(a,b)(a + b))",
                                  onnxMnistSoftmax);
            assertEquals("tensor(d1[10],d2[784])",
                         onnxMnistSoftmax.evaluatorOf("default.add").context().get("constant(mnist_softmax_Variable)").type().toString());

            // Function
            assertEquals(1, onnxMnistSoftmax.functions().size());
            ExpressionFunction function = onnxMnistSoftmax.functions().get(0);
            // assertEquals(TensorType.fromSpec("tensor()"), function.returnType().get()); TODO
            assertEquals(1, function.arguments().size());
            assertEquals("Placeholder", function.arguments().get(0));
            assertEquals(TensorType.fromSpec("tensor(d0[],d1[784])"), function.argumentTypes().get("Placeholder"));

            // Evaluator
            FunctionEvaluator evaluator = onnxMnistSoftmax.evaluatorOf(); // Verify exactly one output available
            assertEquals("Placeholder, constant(mnist_softmax_Variable), constant(mnist_softmax_Variable_1)", evaluator.context().names().stream().sorted().collect(Collectors.joining(", ")));
            assertEquals(-1.6372650861740112E-6, evaluator.evaluate().sum().asDouble(), delta);
        }

        {
            Model tfMnistSoftmax = tester.models().get("mnist_softmax_saved");
            tester.assertFunction("serving_default.y",
                                  "join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), constant(mnist_softmax_saved_layer_Variable_read), f(a,b)(a * b)), sum, d2), constant(mnist_softmax_saved_layer_Variable_1_read), f(a,b)(a + b))",
                                  tfMnistSoftmax);

            // Function
            assertEquals(1, tfMnistSoftmax.functions().size());
            ExpressionFunction function = tfMnistSoftmax.functions().get(0);
            // assertEquals(TensorType.fromSpec("tensor()"), function.returnType().get()); TODO
            assertEquals(1, function.arguments().size());
            assertEquals("x", function.arguments().get(0));
            assertEquals(TensorType.fromSpec("tensor(d0[],d1[784])"), function.argumentTypes().get("x"));

            // Evaluator
            FunctionEvaluator evaluator = tfMnistSoftmax.evaluatorOf(); // Verify exactly one output available
            assertEquals("Placeholder, constant(mnist_softmax_saved_layer_Variable_1_read), constant(mnist_softmax_saved_layer_Variable_read)", evaluator.context().names().stream().sorted().collect(Collectors.joining(", ")));
            assertEquals(-1.6372650861740112E-6, evaluator.evaluate().sum().asDouble(), delta);
        }

        {
            Model tfMnist = tester.models().get("mnist_saved");
            tester.assertFunction("serving_default.y",
                                  "join(reduce(join(map(join(reduce(join(join(join(rankingExpression(imported_ml_function_mnist_saved_dnn_hidden1_add), 0.009999999776482582, f(a,b)(a * b)), rankingExpression(imported_ml_function_mnist_saved_dnn_hidden1_add), f(a,b)(max(a,b))), constant(mnist_saved_dnn_hidden2_weights_read), f(a,b)(a * b)), sum, d3), constant(mnist_saved_dnn_hidden2_bias_read), f(a,b)(a + b)), f(a)(1.050701 * if (a >= 0, a, 1.673263 * (exp(a) - 1)))), constant(mnist_saved_dnn_outputs_weights_read), f(a,b)(a * b)), sum, d2), constant(mnist_saved_dnn_outputs_bias_read), f(a,b)(a + b))",
                                  tfMnist);

            // Generated function
            tester.assertFunction("imported_ml_function_mnist_saved_dnn_hidden1_add",
                                  "join(reduce(join(rename(input, (d0, d1), (d0, d4)), constant(mnist_saved_dnn_hidden1_weights_read), f(a,b)(a * b)), sum, d4), constant(mnist_saved_dnn_hidden1_bias_read), f(a,b)(a + b))",
                                  tfMnist);

            // Function
            assertEquals(2, tfMnist.functions().size()); // TODO: Filter out generated function
            ExpressionFunction function = tfMnist.functions().get(1);
            // assertEquals(TensorType.fromSpec("tensor()"), function.returnType().get()); TODO
            assertEquals(1, function.arguments().size());
            assertEquals("x", function.arguments().get(0));
            assertEquals(TensorType.fromSpec("tensor(d0[],d1[784])"), function.argumentTypes().get("x"));

            // Evaluator
            FunctionEvaluator evaluator = tfMnist.evaluatorOf("serving_default");
            assertEquals("constant(mnist_saved_dnn_hidden1_bias_read), constant(mnist_saved_dnn_hidden1_weights_read), constant(mnist_saved_dnn_hidden2_bias_read), constant(mnist_saved_dnn_hidden2_weights_read), constant(mnist_saved_dnn_outputs_bias_read), constant(mnist_saved_dnn_outputs_weights_read), input, rankingExpression(imported_ml_function_mnist_saved_dnn_hidden1_add)", evaluator.context().names().stream().sorted().collect(Collectors.joining(", ")));
            assertEquals(-0.714629131972222, evaluator.evaluate().sum().asDouble(), delta);
        }
    }

    private String commaSeparated(List<?> items) {
        return items.stream().map(item -> item.toString()).sorted().collect(Collectors.joining(", "));
    }

}
