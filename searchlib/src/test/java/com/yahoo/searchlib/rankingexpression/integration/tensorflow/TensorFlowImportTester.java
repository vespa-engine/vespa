package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;

import java.nio.FloatBuffer;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Helper for TensorFlow import tests.
 * This currently assumes the TensorFlow model takes a single input named Placeholder, of type tensor(d0[1],d1[784])
 *
 * @author bratseth
 */
public class TensorFlowImportTester {

    // Sizes of the "Placeholder" vector
    private final int d0Size = 1;
    private final int d1Size = 784;

    public void assertEqualResult(SavedModelBundle model, TensorFlowModel result, String inputName, String operationName) {
        Tensor tfResult = tensorFlowExecute(model, inputName, operationName);
        Context context = contextFrom(result);
        Tensor placeholder = placeholderArgument();
        context.put(inputName, new TensorValue(placeholder));
        Tensor vespaResult = result.expressions().get(operationName).evaluate(context).asTensor();
        assertEquals("Operation '" + operationName + "' produces equal results", tfResult, vespaResult);
    }

    private Tensor tensorFlowExecute(SavedModelBundle model, String inputName, String operationName) {
        Session.Runner runner = model.session().runner();
        org.tensorflow.Tensor<?> placeholder = org.tensorflow.Tensor.create(new long[]{ d0Size, d1Size },
                                                                            FloatBuffer.allocate(d0Size * d1Size));
        runner.feed(inputName, placeholder);
        List<org.tensorflow.Tensor<?>> results = runner.fetch(operationName).run();
        assertEquals(1, results.size());
        return new TensorConverter().toVespaTensor(results.get(0));
    }

    private Context contextFrom(TensorFlowModel result) {
        MapContext context = new MapContext();
        result.constants().forEach((name, tensor) -> context.put("constant(\"" + name + "\")", new TensorValue(tensor)));
        return context;
    }

    private Tensor placeholderArgument() {
        Tensor.Builder b = Tensor.Builder.of(new TensorType.Builder().indexed("d0", d0Size).indexed("d1", d1Size).build());
        for (int d0 = 0; d0 < d0Size; d0++)
            for (int d1 = 0; d1 < d1Size; d1++)
                b.cell(0, d0, d1);
        return b.build();
    }

}
