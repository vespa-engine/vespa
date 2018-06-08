// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.io.IOUtils;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.gbdtoptimization.GBDTForestOptimizer;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Two small benchmarks of ranking expression evaluation
 *
 * @author bratseth
 */
public class StreamEvaluationBenchmark {

    public void run() {
        try {
            List<Map<String, Double>> features = readFeatures("/Users/bratseth/development/data/stream/gbdtFeatures");
            String streamExpression = readFile("/Users/bratseth/development/data/stream/stream.expression");
            run(streamExpression, features, 10);
        }
        catch (ParseException e) {
            throw new RuntimeException("Benchmarking failed", e);
        }
    }

    private String readFile(String file) {
        try {
            return IOUtils.readFile(new File(file));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Read an ad-hoc file format with some similarly ad hoc code */
    private List<Map<String, Double>> readFeatures(String fileName) {
        try (BufferedReader reader = IOUtils.createReader(fileName)) {
            List<Map<String, Double>> featureItems = new ArrayList<>();
            String line;
            Map<String, Double> featureItem = null;
            while (null != (line = reader.readLine())) {
                if (line.trim().equals("Printing Feature Set")) { // new feature item
                    featureItem = new HashMap<>();
                    featureItems.add(featureItem);
                }
                else { // a feature
                    line = line.replace("Feature key is ", "");
                    line = line.replace(" Feature Value is ", "=");
                    // now we have featurekey=featurevalue
                    String[] keyValue = line.split("=");
                    if (keyValue.length != 2)
                        System.err.println("Skipping invalid feature line '" + line + "'");
                    else
                        featureItem.put(keyValue[0], Double.parseDouble(keyValue[1]));
                }
            }
            System.out.println("Read " + featureItems.size() + " feature items");
            return featureItems;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void run(String expressionString, List<Map<String, Double>> features, int iterations) throws ParseException {
        // Optimize
        RankingExpression expression = new RankingExpression(expressionString);
        DoubleOnlyArrayContext contextPrototype = new DoubleOnlyArrayContext(expression, true);
        OptimizationReport forestOptimizationReport = new ExpressionOptimizer().optimize(expression, contextPrototype);
        System.out.println(forestOptimizationReport);
        System.out.println("Optimized expression: " + expression.getRoot());

        // Warm up
        out("Warming up ...");
        double total = 0;
        total += benchmarkIterations(expression , contextPrototype, features, Math.max(iterations/5, 1));
        oul("done");

        // Benchmark
        out("Running " + iterations + " of 'stream' ...");
        long tStartTime = System.currentTimeMillis();
        total += benchmarkIterations(expression, contextPrototype, features, iterations);
        long totalTime = System.currentTimeMillis() - tStartTime;
        oul("done");
        oul("   Total time running 'stream': " + totalTime +
            " ms (" + totalTime*1000/(iterations*features.size()) + " microseconds/expression)");
    }

    private double benchmarkIterations(RankingExpression gbdt, Context contextPrototype,
                                       List<Map<String, Double>> features, int iterations) {
        // Simulate realistic use: The array context can be reused for a series of evaluations in a thread
        // but each evaluation binds a new set of values.
        double total=0;
        Context context = copyForEvaluation(contextPrototype);
        long totalNanoTime = 0;
        for (int i=0; i<iterations; i++) {
            for (Map<String, Double> featureItem : features) {
                long startTime = System.nanoTime();
                bindStreamingFeatures(featureItem, context);
                total += gbdt.evaluate(context).asDouble();
                totalNanoTime += System.nanoTime() - startTime;
                blowCaches();
            }
        }
        System.out.println("Total time fine-grain measured: " + totalNanoTime/(1000 * iterations * features.size()));
        return total;
    }

    private double blowCaches() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 1000 * 1000; i++) {
            list.add(i);
        }
        double total = 0;
        for (Integer i : list)
            total += i;
        return total;
    }

    private Context copyForEvaluation(Context contextPrototype) {
        if (contextPrototype instanceof AbstractArrayContext) // optimized - contains name to index map
            return ((AbstractArrayContext)contextPrototype).clone();
        else if (contextPrototype instanceof MapContext) // Unoptimized - nothing to keep
            return new MapContext();
        else
            throw new RuntimeException("Unknown context type " + contextPrototype.getClass());
    }

    private void out(String s) {
        System.out.print(s);
    }

    private void oul(String s) {
        System.out.println(s);
    }

    public static void main(String[] args) {
        new StreamEvaluationBenchmark().run();
    }

    private void bindStreamingFeatures(Map<String, Double> featureItem, Context context) {
        for (Map.Entry<String, Double> feature : featureItem.entrySet())
            context.put(feature.getKey(), feature.getValue());
    }

}
