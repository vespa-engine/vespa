// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation.gbdtoptimization;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.ContextIndex;
import com.yahoo.searchlib.rankingexpression.evaluation.OptimizationReport;
import com.yahoo.searchlib.rankingexpression.evaluation.Optimizer;
import com.yahoo.searchlib.rankingexpression.rule.OperationNode;
import com.yahoo.searchlib.rankingexpression.rule.Operator;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author bratseth
 */
public class GBDTForestOptimizer extends Optimizer {

    private OptimizationReport report;

    /**
     * A temporary value used within the algorithm
     */
    private int currentTreesOptimized = 0;

    /**
     * Optimizes sums of GBDTNodes by replacing them by a single GBDTForestNode
     *
     * @param expression the expression to destructively optimize
     * @param context    a fast lookup context created from the given expression
     * @param report     the optimization report to which actions of this is logged
     */
    @Override
    public void optimize(RankingExpression expression, ContextIndex context, OptimizationReport report) {
        if ( ! isEnabled()) return;

        this.report = report;
        expression.setRoot(findAndOptimize(expression.getRoot()));
        report.note("GBDT forest optimization done");
    }

    /**
     * Recursively descend and optimize gbdt forest nodes.
     *
     * @return the resulting node, which may be the input node if no optimizations were found
     */
    private ExpressionNode findAndOptimize(ExpressionNode node) {
        ExpressionNode newNode = optimize(node);
        if ( ! (newNode instanceof CompositeNode newComposite)) return newNode;

        List<ExpressionNode> newChildren = new ArrayList<>();
        for (ExpressionNode child : newComposite.children()) {
            newChildren.add(findAndOptimize(child));
        }
        return newComposite.setChildren(newChildren);
    }

    /**
     * Optimize the given node (only)
     *
     * @return the resulting node, which may be the input node if it could not be optimized
     */
    private ExpressionNode optimize(ExpressionNode node) {
        currentTreesOptimized = 0;
        List<Double> forest = new ArrayList<>();
        boolean optimized = optimize(node, forest);
        if ( ! optimized ) return node;

        GBDTForestNode forestNode = new GBDTForestNode(toArray(forest));
        report.incMetric("Number of forests", 1);
        report.incMetric("GBDT trees optimized to forests", currentTreesOptimized);
        return forestNode;
    }

    /**
     * Optimize the given node, if it is the root of a gdbt forest. Otherwise do nothing and return false
     */
    private boolean optimize(ExpressionNode node, List<Double> forest) {
        if (node instanceof GBDTNode) {
            addTo(forest, (GBDTNode)node);
            currentTreesOptimized++;
            return true;
        }
        if (!(node instanceof OperationNode aNode)) {
            return false;
        }
        for (Operator op : aNode.operators()) {
            if (op != Operator.plus) {
                return false;
            }
        }
        for (ExpressionNode child : aNode.children()) {
            if (!optimize(child, forest)) {
                return false;
            }
        }
        return true;
    }

    private void addTo(List<Double> forest, GBDTNode tree) {
        forest.add((double)tree.values().length);
        addAll(tree.values(), forest);
    }

    private void addAll(double[] values, List<Double> forest) {
        for (double value : values) {
            forest.add(value);
        }
    }

    private double[] toArray(List<Double> valueList) {
        double[] valueArray = new double[valueList.size()];
        for (int i = 0; i < valueList.size(); i++) {
            valueArray[i] = valueList.get(i);
        }
        return valueArray;
    }

}
