// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.ImportedModel;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author lesters
 */
public class SimpleImportTestCase {

    @Test
    public void testSimpleOnnxModelImport() {
        ImportedModel model = new OnnxImporter().importModel("test", "src/test/models/onnx/simple/simple.onnx");

        assertFalse(model.isNative());
        model = model.asNative();
        assertTrue(model.isNative());

        MapContext context = new MapContext();
        context.put("query_tensor", new TensorValue(Tensor.from("tensor(d0[1],d1[4]):[0.1, 0.2, 0.3, 0.4]")));
        context.put("attribute_tensor", new TensorValue(Tensor.from("tensor(d0[4],d1[1]):[0.1, 0.2, 0.3, 0.4]")));
        context.put("bias_tensor", new TensorValue(Tensor.from("tensor(d0[1],d1[1]):[1.0]")));

        Tensor result = model.expressions().get("output").evaluate(context).asTensor();
        assertEquals(result, Tensor.from("tensor(d0[1],d1[1]):{{d0:0,d1:0}:1.3}"));
    }

    @Test
    public void testGather() {
        ImportedModel model = new OnnxImporter().importModel("test", "src/test/models/onnx/simple/gather.onnx").asNative();

        MapContext context = new MapContext();
        context.put("data", new TensorValue(Tensor.from("tensor(d0[3],d1[2]):[1, 2, 3, 4, 5, 6]")));
        context.put("indices", new TensorValue(Tensor.from("tensor(d0[2],d1[2]):[0, 1, 1, 2]")));

        model.functions().forEach((k, v) -> evaluateFunction(context, model, k));

        Tensor result = model.expressions().get("y").evaluate(context).asTensor();
        assertEquals(result, Tensor.from("tensor(d0[2],d1[2],d2[2]):[1, 2, 3, 4, 3, 4, 5, 6]"));
    }

    @Test
    public void testConcat() {
        ImportedModel model = new OnnxImporter().importModel("test", "src/test/models/onnx/simple/concat.onnx").asNative();

        MapContext context = new MapContext();
        context.put("i", new TensorValue(Tensor.from("tensor(d0[1]):[1]")));
        context.put("j", new TensorValue(Tensor.from("tensor(d0[1]):[2]")));
        context.put("k", new TensorValue(Tensor.from("tensor(d0[1]):[3]")));

        Tensor result = model.expressions().get("y").evaluate(context).asTensor();
        assertEquals(result, Tensor.from("tensor(d0[3]):[1, 2, 3]"));
    }

    private void evaluateFunction(Context context, ImportedModel model, String functionName) {
        if (!context.names().contains(functionName)) {
            RankingExpression e = RankingExpression.from(model.functions().get(functionName));
            evaluateFunctionDependencies(context, model, e.getRoot());
            context.put(functionName, new TensorValue(e.evaluate(context).asTensor()));
        }
    }

    private void evaluateFunctionDependencies(Context context, ImportedModel model, ExpressionNode node) {
        if (node instanceof ReferenceNode) {
            String name = node.toString();
            if (model.functions().containsKey(name)) {
                evaluateFunction(context, model, name);
            }
        }
        else if (node instanceof CompositeNode) {
            for (ExpressionNode child : ((CompositeNode)node).children()) {
                evaluateFunctionDependencies(context, model, child);
            }
        }
    }

}
