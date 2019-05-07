// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlFunction;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import ai.vespa.rankingexpression.importer.ImportedModel;
import ai.vespa.rankingexpression.importer.tensorflow.TensorFlowImporter;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author lesters
 */
public class OnnxMnistSoftmaxImportTestCase {

    @Test
    public void testMnistSoftmaxImport() {
        ImportedModel model = new OnnxImporter().importModel("test", "src/test/models/onnx/mnist_softmax/mnist_softmax.onnx");

        // Check constants
        assertEquals(2, model.largeConstants().size());

        Tensor constant0 = Tensor.from(model.largeConstants().get("test_Variable"));
        assertNotNull(constant0);
        assertEquals(new TensorType.Builder(TensorType.Value.FLOAT).indexed("d2", 784).indexed("d1", 10).build(),
                     constant0.type());
        assertEquals(7840, constant0.size());

        Tensor constant1 = Tensor.from(model.largeConstants().get("test_Variable_1"));
        assertNotNull(constant1);
        assertEquals(new TensorType.Builder(TensorType.Value.FLOAT).indexed("d1", 10).build(), constant1.type());
        assertEquals(10, constant1.size());

        // Check inputs
        assertEquals(1, model.inputs().size());
        assertTrue(model.inputs().containsKey("Placeholder"));
        assertEquals(TensorType.fromSpec("tensor<float>(d0[],d1[784])"), model.inputs().get("Placeholder"));

        // Check signature
        ImportedMlFunction output = model.defaultSignature().outputFunction("add", "add");
        assertNotNull(output);
        assertEquals("join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), constant(test_Variable), f(a,b)(a * b)), sum, d2), constant(test_Variable_1), f(a,b)(a + b))",
                     output.expression());
        assertEquals(TensorType.fromSpec("tensor<float>(d0[],d1[784])"),
                     model.inputs().get(model.defaultSignature().inputs().get("Placeholder")));
        assertEquals("{Placeholder=tensor<float>(d0[],d1[784])}", output.argumentTypes().toString());
    }

    @Test
    public void testComparisonBetweenOnnxAndTensorflow() {
        String tfModelPath = "src/test/models/tensorflow/mnist_softmax/saved";
        String onnxModelPath = "src/test/models/onnx/mnist_softmax/mnist_softmax.onnx";

        Tensor argument = placeholderArgument();
        Tensor tensorFlowResult = evaluateTensorFlowModel(tfModelPath, argument, "Placeholder", "add");
        Tensor onnxResult = evaluateOnnxModel(onnxModelPath, argument, "Placeholder", "add");

        assertEquals("Operation 'add' produces equal results", tensorFlowResult, onnxResult);
    }

    private Tensor evaluateTensorFlowModel(String path, Tensor argument, String input, String output) {
        ImportedModel model = new TensorFlowImporter().importModel("test", path);
        return evaluateExpression(model.expressions().get(output), contextFrom(model), argument, input);
    }

    private Tensor evaluateOnnxModel(String path, Tensor argument, String input, String output) {
        ImportedModel model = new OnnxImporter().importModel("test", path);
        return evaluateExpression(model.expressions().get(output), contextFrom(model), argument, input);
    }

    private Tensor evaluateExpression(RankingExpression expression, Context context, Tensor argument, String input) {
        context.put(input, new TensorValue(argument));
        return expression.evaluate(context).asTensor();
    }

    private Context contextFrom(ImportedModel result) {
        MapContext context = new MapContext();
        result.largeConstants().forEach((name, tensor) -> context.put("constant(" + name + ")", new TensorValue(Tensor.from(tensor))));
        result.smallConstants().forEach((name, tensor) -> context.put("constant(" + name + ")", new TensorValue(Tensor.from(tensor))));
        return context;
    }

    private Tensor placeholderArgument() {
        Tensor.Builder b = Tensor.Builder.of(new TensorType.Builder().indexed("d0", 1).indexed("d1", 784).build());
        for (int d0 = 0; d0 < 1; d0++)
            for (int d1 = 0; d1 < 784; d1++)
                b.cell(d1 * 1.0 / 784, d0, d1);
        return b.build();
    }


}
