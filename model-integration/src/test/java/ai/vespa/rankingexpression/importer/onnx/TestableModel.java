// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.ImportedModel;
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

import java.util.Map;

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
        result.largeConstantTensors().forEach((name, tensor) -> context.put("constant(" + name + ")", new TensorValue(tensor)));
        result.smallConstantTensors().forEach((name, tensor) -> context.put("constant(" + name + ")", new TensorValue(tensor)));
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
