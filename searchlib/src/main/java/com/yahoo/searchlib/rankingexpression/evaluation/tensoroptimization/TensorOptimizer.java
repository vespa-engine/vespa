// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation.tensoroptimization;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.ContextIndex;
import com.yahoo.searchlib.rankingexpression.evaluation.OptimizationReport;
import com.yahoo.searchlib.rankingexpression.evaluation.Optimizer;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.ReduceJoin;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * Recognizes and optimizes tensor expressions.
 *
 * @author lesters
 */
public class TensorOptimizer extends Optimizer {

    private OptimizationReport report;

    @Override
    public void optimize(RankingExpression expression, ContextIndex context, OptimizationReport report) {
        if (!isEnabled()) return;
        this.report = report;
        expression.setRoot(optimize(expression.getRoot(), context));
        report.note("Tensor expression optimization done");
    }

    private ExpressionNode optimize(ExpressionNode node, ContextIndex context) {
        node = optimizeReduceJoin(node);
        if (node instanceof CompositeNode) {
            return optimizeChildren((CompositeNode)node, context);
        }
        return node;
    }

    private ExpressionNode optimizeChildren(CompositeNode node, ContextIndex context) {
        List<ExpressionNode> children = node.children();
        List<ExpressionNode> optimizedChildren = new ArrayList<>(children.size());
        for (ExpressionNode child : children)
            optimizedChildren.add(optimize(child, context));
        return node.setChildren(optimizedChildren);
    }

    /**
     * Recognized a reduce followed by a join. In many cases, chunking these
     * two operations together is significantly more efficient than evaluating
     * each on its own, avoiding the cost of a temporary tensor.
     *
     * Note that this does not guarantee that the optimization is performed.
     * The ReduceJoin class determines whether or not the arguments are
     * compatible with the optimization.
     */
    private ExpressionNode optimizeReduceJoin(ExpressionNode node) {
        if ( ! (node instanceof TensorFunctionNode)) {
            return node;
        }
        TensorFunction<Reference> function = ((TensorFunctionNode) node).function();
        if ( ! (function instanceof Reduce)) {
            return node;
        }
        List<ExpressionNode> children = ((TensorFunctionNode) node).children();
        if (children.size() != 1) {
            return node;
        }
        ExpressionNode child = children.get(0);
        if ( ! (child instanceof TensorFunctionNode)) {
            return node;
        }
        TensorFunction<Reference> argument = ((TensorFunctionNode) child).function();
        if (argument instanceof Join) {
            report.incMetric("Replaced reduce->join", 1);
            return new TensorFunctionNode(new ReduceJoin<>((Reduce<Reference>)function, (Join<Reference>)argument));
        }
        return node;
    }

}
