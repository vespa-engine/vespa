// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.ImportedModel;
import ai.vespa.rankingexpression.importer.tensorflow.TensorConverter;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.ContextIndex;
import com.yahoo.searchlib.rankingexpression.evaluation.ExpressionOptimizer;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TestableModel {

    Tensor evaluateVespa(ImportedModel model, String operationName, Map<String, TensorType> inputs) {
        Context context = contextFrom(model);
        for (Map.Entry<String, TensorType> entry : inputs.entrySet()) {
            Tensor argument = vespaInputArgument(1, entry.getValue().dimensions().get(1).size().get().intValue());
            context.put(entry.getKey(), new TensorValue(argument));
        }
        model.functions().forEach((k, v) -> evaluateFunction(context, model, k));
        RankingExpression expression = model.expressions().get(operationName);
        ExpressionOptimizer optimizer = new ExpressionOptimizer();
        optimizer.optimize(expression, (ContextIndex)context);
        return expression.evaluate(context).asTensor();
    }

    Tensor evaluateTF(SavedModelBundle tensorFlowModel, String operationName, Map<String, TensorType> inputs) {
        Session.Runner runner = tensorFlowModel.session().runner();
        for (Map.Entry<String, TensorType> entry : inputs.entrySet()) {
            try {
                runner.feed(entry.getKey(), tensorFlowFloatInputArgument(1, entry.getValue().dimensions().get(1).size().get().intValue()));
            } catch (Exception e) {
                runner.feed(entry.getKey(), tensorFlowDoubleInputArgument(1, entry.getValue().dimensions().get(1).size().get().intValue()));
            }
        }
        List<org.tensorflow.Tensor<?>> results = runner.fetch(operationName).run();
        assertEquals(1, results.size());
        return TensorConverter.toVespaTensor(results.get(0));
    }

    private org.tensorflow.Tensor<?> tensorFlowFloatInputArgument(int d0Size, int d1Size) {
        FloatBuffer fb1 = FloatBuffer.allocate(d0Size * d1Size);
        int i = 0;
        for (int d0 = 0; d0 < d0Size; d0++)
            for (int d1 = 0; d1 < d1Size; ++d1)
                fb1.put(i++, (float)(d1 * 1.0 / d1Size));
        return org.tensorflow.Tensor.create(new long[]{ d0Size, d1Size }, fb1);
    }

    private org.tensorflow.Tensor<?> tensorFlowDoubleInputArgument(int d0Size, int d1Size) {
        DoubleBuffer fb1 = DoubleBuffer.allocate(d0Size * d1Size);
        int i = 0;
        for (int d0 = 0; d0 < d0Size; d0++)
            for (int d1 = 0; d1 < d1Size; ++d1)
                fb1.put(i++, (float)(d1 * 1.0 / d1Size));
        return org.tensorflow.Tensor.create(new long[]{ d0Size, d1Size }, fb1);
    }

    private Tensor vespaInputArgument(int d0Size, int d1Size) {
        Tensor.Builder b = Tensor.Builder.of(new TensorType.Builder().indexed("d0", d0Size).indexed("d1", d1Size).build());
        for (int d0 = 0; d0 < d0Size; d0++)
            for (int d1 = 0; d1 < d1Size; d1++)
                b.cell(d1 * 1.0 / d1Size, d0, d1);
        return b.build();
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

    static Context contextFrom(ImportedModel result) {
        TestableModelContext context = new TestableModelContext();
        result.largeConstants().forEach((name, tensor) -> context.put("constant(" + name + ")", new TensorValue(Tensor.from(tensor))));
        result.smallConstants().forEach((name, tensor) -> context.put("constant(" + name + ")", new TensorValue(Tensor.from(tensor))));
        return context;
    }

    private static class TestableModelContext extends MapContext implements ContextIndex {
        @Override
        public int size() {
            return bindings().size();
        }
        @Override
        public int getIndex(String name) {
            throw new UnsupportedOperationException(this + " does not support index lookup by name");
        }
    }

}
