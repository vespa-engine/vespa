// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.tensorflow.TensorConverter;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;

import java.nio.FloatBuffer;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Helper for TensorFlow import tests: Imports a model and provides asserts on it.
 * This currently assumes the TensorFlow model takes a single input of type tensor(d0[1],d1[784])
 *
 * @author bratseth
 */
public class TestableTensorFlowModel {

    private SavedModelBundle tensorFlowModel;
    private ImportedModel model;

    // Sizes of the input vector
    private final int d0Size = 1;
    private final int d1Size = 784;

    public TestableTensorFlowModel(String modelName, String modelDir) {
        tensorFlowModel = SavedModelBundle.load(modelDir, "serve");
        model = new TensorFlowImporter().importModel(modelName, tensorFlowModel);
    }

    public ImportedModel get() { return model; }

    public void assertEqualResult(String inputName, String operationName) {
        Tensor tfResult = tensorFlowExecute(tensorFlowModel, inputName, operationName);
        Context context = contextFrom(model);
        Tensor placeholder = placeholderArgument();
        context.put(inputName, new TensorValue(placeholder));

        model.macros().forEach((k,v) -> evaluateMacro(context, model, k));

        Tensor vespaResult = model.expressions().get(operationName).evaluate(context).asTensor();
        assertEquals("Operation '" + operationName + "' produces equal results", tfResult, vespaResult);
    }

    private Tensor tensorFlowExecute(SavedModelBundle model, String inputName, String operationName) {
        Session.Runner runner = model.session().runner();
        FloatBuffer fb = FloatBuffer.allocate(d0Size * d1Size);
        for (int i = 0; i < d1Size; ++i) {
            fb.put(i, (float)(i * 1.0 / d1Size));
        }
        org.tensorflow.Tensor<?> placeholder = org.tensorflow.Tensor.create(new long[]{ d0Size, d1Size }, fb);
        runner.feed(inputName, placeholder);
        List<org.tensorflow.Tensor<?>> results = runner.fetch(operationName).run();
        assertEquals(1, results.size());
        return TensorConverter.toVespaTensor(results.get(0));
    }

    private Context contextFrom(ImportedModel result) {
        MapContext context = new MapContext();
        result.largeConstants().forEach((name, tensor) -> context.put("constant(" + name + ")", new TensorValue(tensor)));
        result.smallConstants().forEach((name, tensor) -> context.put("constant(" + name + ")", new TensorValue(tensor)));
        return context;
    }

    private Tensor placeholderArgument() {
        Tensor.Builder b = Tensor.Builder.of(new TensorType.Builder().indexed("d0", d0Size).indexed("d1", d1Size).build());
        for (int d0 = 0; d0 < d0Size; d0++)
            for (int d1 = 0; d1 < d1Size; d1++)
                b.cell(d1 * 1.0 / d1Size, d0, d1);
        return b.build();
    }

    private void evaluateMacro(Context context, ImportedModel model, String macroName) {
        if (!context.names().contains(macroName)) {
            RankingExpression e = model.macros().get(macroName);
            evaluateMacroDependencies(context, model, e.getRoot());
            context.put(macroName, new TensorValue(e.evaluate(context).asTensor()));
        }
    }

    private void evaluateMacroDependencies(Context context, ImportedModel model, ExpressionNode node) {
        if (node instanceof ReferenceNode) {
            String name = node.toString();
            if (model.macros().containsKey(name)) {
                evaluateMacro(context, model, name);
            }
        }
        else if (node instanceof CompositeNode) {
            for (ExpressionNode child : ((CompositeNode)node).children()) {
                evaluateMacroDependencies(context, model, child);
            }
        }
    }

}
