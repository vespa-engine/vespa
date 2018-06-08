// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.gbdtoptimization.GBDTForestOptimizer;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Simon Thoresen
 */
public final class Benchmark {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Benchmark <filename> [<iterations>]");
            System.exit(1);
        }
        int numRuns = 1000;
        if (args.length == 2) {
            numRuns = Integer.valueOf(args[1]);
        }
        List<Result> res = new ArrayList<Result>();
        try {
            BufferedReader in = new BufferedReader(new FileReader(args[0]));
            StringBuilder str = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                str.append(line);
            }
            String exp = str.toString();
            res.add(evaluateTree(exp, numRuns));
            res.add(evaluateTreeOptimized(exp, numRuns));
            res.add(evaluateForestOptimized(exp, numRuns));
        } catch (IOException e) {
            System.out.println("An error occured while reading the content of file '" + args[0] + "': " + e);
            System.exit(1);
        } catch (ParseException e) {
            System.out.println("An error occured while parsing the content of file '" + args[0] + "': " + e);
            System.exit(1);
        }
        for (Result lhs : res) {
            for (Result rhs : res) {
                if (lhs.res < rhs.res - 1e-6 || lhs.res > rhs.res + 1e-6) {
                    System.err.println("Evaluation of '" + lhs.name + "' and '" + rhs.name + "' disagree on result; " +
                                       "expected " + lhs.res + ", got " + rhs.res + ".");
                    System.exit(1);
                }
            }
            System.out.format("%1$-16s : %2$8.04f ms (%3$-6.04f)\n",
                              lhs.name, lhs.millis, res.get(0).millis / lhs.millis);
        }
    }

    private static Result evaluateTree(String str, int numRuns) throws ParseException {
        Result ret = new Result();
        ret.name = "Unoptimized";

        RankingExpression exp = new RankingExpression(str);
        List<String> vars = new LinkedList<String>();
        getFeatures(exp.getRoot(), vars);

        benchmark(exp, vars, new MapContext(), numRuns, ret);
        return ret;
    }

    private static Result evaluateTreeOptimized(String str, int numRuns) throws ParseException {
        Result ret = new Result();
        ret.name = "Optimized tree";

        RankingExpression exp = new RankingExpression(str);
        List<String> vars = new LinkedList<String>();
        getFeatures(exp.getRoot(), vars);

        ArrayContext ctx = new ArrayContext(exp);
        ExpressionOptimizer optimizer = new ExpressionOptimizer();
        optimizer.getOptimizer(GBDTForestOptimizer.class).setEnabled(false);
        optimizer.optimize(exp, ctx);

        benchmark(exp, vars, ctx, numRuns, ret);
        return ret;
    }

    private static Result evaluateForestOptimized(String str, int numRuns) throws ParseException {
        Result ret = new Result();
        ret.name = "Optimized forest";

        RankingExpression exp = new RankingExpression(str);
        List<String> vars = new LinkedList<String>();
        getFeatures(exp.getRoot(), vars);

        ArrayContext ctx = new ArrayContext(exp);
        ExpressionOptimizer optimizer = new ExpressionOptimizer();
        optimizer.optimize(exp, ctx);

        benchmark(exp, vars, ctx, numRuns, ret);
        return ret;
    }

    private static void benchmark(RankingExpression exp, List<String> vars, Context ctx, int numRuns, Result out) {
        for (int i = 0, len = vars.size(); i < len; ++i) {
            ctx.put(vars.get(i), i / (double)len);
        }
        for (int i = 0; i < numRuns; ++i) {
            out.res = exp.evaluate(ctx).asDouble();
        }
        long begin = System.nanoTime();
        for (int i = 0; i < numRuns; ++i) {
            out.res = exp.evaluate(ctx).asDouble();
        }
        long end = System.nanoTime();

        out.millis = (end - begin) / (1000.0 * 1000.0);
    }

    private static void getFeatures(ExpressionNode node, List<String> out) {
        if (node instanceof ReferenceNode) {
            String feature = ((ReferenceNode)node).getName();
            if (!out.contains(feature)) {
                out.add(feature);
            }
        } else if (node instanceof CompositeNode) {
            CompositeNode cNode = (CompositeNode)node;
            for (ExpressionNode child : cNode.children()) {
                getFeatures(child, out);
            }
        }
    }

    private static class Result {
        String name = "anonymous";
        double millis = Double.MAX_VALUE;
        double res = 0;
    }
}
