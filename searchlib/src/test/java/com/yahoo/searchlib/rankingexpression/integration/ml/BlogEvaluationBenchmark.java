// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.ContextIndex;
import com.yahoo.searchlib.rankingexpression.evaluation.ExpressionOptimizer;
import com.yahoo.searchlib.rankingexpression.evaluation.OptimizationReport;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.tensorflow.TensorConverter;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;

import java.nio.FloatBuffer;
import java.util.List;

import static com.yahoo.searchlib.rankingexpression.integration.ml.TestableTensorFlowModel.contextFrom;

/**
 * Microbenchmark of imported ML model evaluation.
 *
 * @author lesters
 */
public class BlogEvaluationBenchmark {

    static final String modelDir = "src/test/files/integration/tensorflow/blog/saved";

    public static void main(String[] args) throws ParseException {
        SavedModelBundle tensorFlowModel = SavedModelBundle.load(modelDir, "serve");
        ImportedModel model = new TensorFlowImporter().importModel("blog", modelDir, tensorFlowModel);

        Context context = contextFrom(model);
        Tensor u = generateInputTensor();
        Tensor d = generateInputTensor();
        context.put("input_u", new TensorValue(u));
        context.put("input_d", new TensorValue(d));

        // Parse the ranking expression from imported string to force primitive tensor functions.
        RankingExpression expression = new RankingExpression(model.expressions().get("y").getRoot().toString());
        benchmarkJava(expression, context, 20, 200);

        System.out.println("*** Optimizing expression ***");
        ExpressionOptimizer optimizer = new ExpressionOptimizer();
        OptimizationReport report = optimizer.optimize(expression, (ContextIndex)context);
        System.out.println(report.toString());

        benchmarkJava(expression, context, 2000, 20000);
        benchmarkTensorFlow(tensorFlowModel, 2000, 20000);
    }

    private static void benchmarkJava(RankingExpression expression, Context context, int warmup, int iterations) {
        System.out.println("*** Java evaluation - warmup ***");
        evaluate(expression, context, warmup);
        System.gc();
        System.out.println("*** Java evaluation - " + iterations + " iterations ***");
        double startTime = System.nanoTime();
        evaluate(expression, context, iterations);
        double endTime = System.nanoTime();
        System.out.println("Model evaluation time is " + ((endTime-startTime) / (1000*1000)) + " ms");
        System.out.println("Average model evaluation time is " + ((endTime-startTime) / (1000*1000)) / iterations + " ms");
    }

    private static double evaluate(RankingExpression expression, Context context, int iterations) {
        double result = 0;
        for (int i = 0 ; i < iterations; i++) {
            result = expression.evaluate(context).asTensor().sum().asDouble();
        }
        return result;
    }

    private static Tensor generateInputTensor() {
        Tensor.Builder b = Tensor.Builder.of(new TensorType.Builder().indexed("d0", 1).indexed("d1", 128).build());
        for (int d0 = 0; d0 < 1; d0++)
            for (int d1 = 0; d1 < 128; d1++)
                b.cell(d1 * 1.0 / 128, d0, d1);
        return b.build();
    }

    private static void benchmarkTensorFlow(SavedModelBundle tensorFlowModel, int warmup, int iterations) {
        org.tensorflow.Tensor<?> u = generateInputTensorFlow();
        org.tensorflow.Tensor<?> d = generateInputTensorFlow();

        System.out.println("*** TensorFlow evaluation - warmup ***");
        evaluateTensorflow(tensorFlowModel, u, d, warmup);

        System.gc();
        System.out.println("*** TensorFlow evaluation - " + iterations + " iterations ***");
        double startTime = System.nanoTime();
        evaluateTensorflow(tensorFlowModel, u, d, iterations);
        double endTime = System.nanoTime();
        System.out.println("Model evaluation time is " + ((endTime-startTime) / (1000*1000) + " ms"));
        System.out.println("Average model evaluation time is " + ((endTime-startTime) / (1000*1000)) / iterations + " ms");
    }

    private static double evaluateTensorflow(SavedModelBundle tensorFlowModel, org.tensorflow.Tensor<?> u, org.tensorflow.Tensor<?> d, int iterations) {
        double result = 0;
        for (int i = 0 ; i < iterations; i++) {
            Session.Runner runner = tensorFlowModel.session().runner();
            runner.feed("input_u", u);
            runner.feed("input_d", d);
            List<org.tensorflow.Tensor<?>> results = runner.fetch("y").run();
            result = TensorConverter.toVespaTensor(results.get(0)).sum().asDouble();
        }
        return result;
    }

    private static org.tensorflow.Tensor<?> generateInputTensorFlow() {
        FloatBuffer fb = FloatBuffer.allocate(1 * 128);
        for (int i = 0; i < 128; ++i) {
            fb.put(i, (float)(i * 1.0 / 128));
        }
        return org.tensorflow.Tensor.create(new long[]{ 1, 128 }, fb);
    }

}
