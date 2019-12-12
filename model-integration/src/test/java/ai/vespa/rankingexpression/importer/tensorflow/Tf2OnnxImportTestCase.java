// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.tensorflow;

import ai.vespa.rankingexpression.importer.ImportedModel;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author lesters
 */
public class Tf2OnnxImportTestCase {

    @Test
    public void testConversionFromTensorFlowToOnnx() {
        String modelPath = "src/test/models/tensorflow/mnist_softmax/saved";
        String modelPathToConvert = "src/test/models/tensorflow/mnist_softmax/tf_2_onnx";

        Tensor argument = placeholderArgument();
        Tensor tensorFlowResult = evaluateTensorFlowModel(modelPath, argument, "Placeholder", "add");
        Tensor tf2OnnxResult = evaluateTensorFlowModel(modelPathToConvert, argument, "Placeholder", "add");

        assertEquals("Operation 'add' produces equal results", tensorFlowResult, tf2OnnxResult);
    }

    private Tensor evaluateTensorFlowModel(String path, Tensor argument, String input, String output) {
        ImportedModel model = new TensorFlowImporter().importModel("test", path);
        String outputExpr = model.signatures().values().iterator().next().outputs().values().iterator().next();
        return evaluateExpression(model.expressions().get(outputExpr), contextFrom(model), argument, input);
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
