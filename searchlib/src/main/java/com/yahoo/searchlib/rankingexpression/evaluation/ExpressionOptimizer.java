// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.gbdtoptimization.GBDTForestOptimizer;
import com.yahoo.searchlib.rankingexpression.evaluation.gbdtoptimization.GBDTOptimizer;
import com.yahoo.searchlib.rankingexpression.evaluation.tensoroptimization.TensorOptimizer;

/**
 * This class will perform various optimizations on the ranking expressions. Clients using optimized expressions
 * will do
 *
 * <code>
 * // Set up once
 * RankingExpression expression = new RankingExpression(myExpressionString);
 * ArrayContext context = new ArrayContext(expression);
 * new ExpressionOptimizer().optimize(expression, context);
 *
 * // Execute repeatedly
 * context.put("featureName1", value1);
 * ...
 * expression.evaluate(context);
 *
 * // Note that the expression may be used by multiple threads at the same time, while the
 * // context is single-threaded. To create a context for another tread, use the above context as a prototype,
 * // contextForOtherThread = context.clone();
 * </code>
 * <p>
 * Instances of this class are not multithread safe.
 *
 * @author bratseth
 */
public class ExpressionOptimizer {

    private final GBDTOptimizer gbdtOptimizer = new GBDTOptimizer();
    private final GBDTForestOptimizer gbdtForestOptimizer = new GBDTForestOptimizer();
    private final TensorOptimizer tensorOptimizer = new TensorOptimizer();

    /** Gets an optimizer instance used by this by class name, or null if the optimizer is not known */
    public Optimizer getOptimizer(Class<?> clazz) {
        if (clazz == gbdtOptimizer.getClass())
            return gbdtOptimizer;
        if (clazz == gbdtForestOptimizer.getClass())
            return gbdtForestOptimizer;
        if (clazz == tensorOptimizer.getClass())
            return tensorOptimizer;
        return null;
    }

    public OptimizationReport optimize(RankingExpression expression, ContextIndex contextIndex) {
        OptimizationReport report = new OptimizationReport();
        // Note: Order of optimizations matter
        gbdtOptimizer.optimize(expression, contextIndex, report);
        gbdtForestOptimizer.optimize(expression, contextIndex, report);
        tensorOptimizer.optimize(expression, contextIndex, report);
        return report;
    }

    public OptimizationReport optimize(RankingExpression expression, AbstractArrayContext arrayContext) {
        return optimize(expression, (ContextIndex)arrayContext);
    }

}
