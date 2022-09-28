// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation.gbdtoptimization;

import com.yahoo.yolean.Exceptions;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.*;
import com.yahoo.searchlib.rankingexpression.rule.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <p>This ranking expression processor recognizes and optimizes GBDT expressions. Note that this optimization is
 * destructive - inspection is not possible into optimized subtrees.</p>
 *
 * <p>This class is not multithread safe.</p>
 *
 * @author bratseth
 */
public class GBDTOptimizer extends Optimizer {

    private OptimizationReport report;

    /**
     * Optimizes this by replacing GBDT sub-expressions by GBDTNodes. These optimized expressions <b>must</b> be
     * executed using an instance of {@link com.yahoo.searchlib.rankingexpression.evaluation.ArrayContext} as context.
     * These thread locally reusable contexts must be created from the ranking expression <i>before</i> the ranking
     * expression is optimized.
     *
     * @param expression the expression to destructively optimize
     * @param context    a fast lookup context created from the given expression
     * @param report     the optimization report to which actions of this is logged
     */
    @Override
    public void optimize(RankingExpression expression, ContextIndex context, OptimizationReport report) {
        if (!isEnabled()) return;

        this.report = report;

        if (context.size() > GBDTNode.MAX_VARIABLES) {
            report.note("Can not optimize expressions referencing more than " + GBDTNode.MAX_VARIABLES + " features: " +
                        expression + " has " + context.size());
            return;
        }

        expression.setRoot(optimize(expression.getRoot(), context));
        report.note("GBDT tree optimization done");
    }

    /**
     * <p>Recursively optimize nodes of the form ArithmeticNode(IfNode,ArithmeticNode(IfNode)) etc., ignore
     * anything else.</p>
     *
     * <p>Each condition node is converted to the double sequence [(OperatorIsEquals ? GBDTNode.MAX_VARIABLES : 0) +
     * IndexOfLeftComparisonFeature+GBDTNode.MAX_LEAF_VALUE, ValueOfRightComparisonValue,#OfValuesInTrueBranch,true
     * branch values,false branch values]</p>
     *
     * <p>Each value node is converted to the double value of the value node itself.</p>
     *
     * @return the optimized expression
     */
    private ExpressionNode optimize(ExpressionNode node, ContextIndex context) {
        if (node instanceof OperationNode) {
            Iterator<ExpressionNode> childIt = ((OperationNode)node).children().iterator();
            ExpressionNode ret = optimize(childIt.next(), context);

            Iterator<Operator> operIt = ((OperationNode)node).operators().iterator();
            while (childIt.hasNext() && operIt.hasNext()) {
                ret = OperationNode.resolve(ret, operIt.next(), optimize(childIt.next(), context));
            }
            return ret;
        }
        if (node instanceof IfNode) {
            return createGBDTNode((IfNode)node, context);
        }
        return node;
    }

    private ExpressionNode createGBDTNode(IfNode cNode,ContextIndex context) {
        List<Double> values = new ArrayList<>();
        try {
            consumeNode(cNode, values, context);
        }
        catch (IllegalArgumentException e) { // Conversion was impossible
            report.note("Skipped optimization: " + Exceptions.toMessageString(e) + ". Expression: " + cNode);
            return cNode;
        }
        report.incMetric("Optimized GDBT trees",1);
        return new GBDTNode(toArray(values));
    }

    /**
     * Recursively consume nodes into the value list Returns the number of values produced by this.
     */
    private int consumeNode(ExpressionNode node, List<Double> values, ContextIndex context) {
        int beforeIndex = values.size();
        if ( node instanceof IfNode) {
            IfNode ifNode = (IfNode)node;
            int jumpValueIndex = consumeIfCondition(ifNode.getCondition(), values, context);
            values.add(0d); // jumpValue goes here after the next line
            int jumpValue = consumeNode(ifNode.getTrueExpression(), values, context) + 1;
            values.set(jumpValueIndex, (double) jumpValue);
            consumeNode(ifNode.getFalseExpression(), values, context);
        } else {
            double value = toValue(node);
            if (Math.abs(value) > GBDTNode.MAX_LEAF_VALUE) {
                throw new IllegalArgumentException("Leaf value is too large for optimization: " + value);
            }
            values.add(toValue(node));
        }
        return values.size() - beforeIndex;
    }

    /** Consumes the if condition and return the size of the values resulting, for convenience */
    private int consumeIfCondition(ExpressionNode condition, List<Double> values, ContextIndex context) {
        if (isBinaryComparison(condition)) {
            OperationNode comparison = (OperationNode)condition;
            if (comparison.operators().get(0) == Operator.smaller)
                values.add(GBDTNode.MAX_LEAF_VALUE + GBDTNode.MAX_VARIABLES*0 + getVariableIndex(comparison.children().get(0), context));
            else if (comparison.operators().get(0) == Operator.equal)
                values.add(GBDTNode.MAX_LEAF_VALUE + GBDTNode.MAX_VARIABLES*1 + getVariableIndex(comparison.children().get(0), context));
            else
                throw new IllegalArgumentException("Cannot optimize other conditions than < and ==, encountered: " + comparison.operators().get(0));
            values.add(toValue(comparison.children().get(1)));
        }
        else if (condition instanceof SetMembershipNode) {
            SetMembershipNode setMembership = (SetMembershipNode)condition;
            values.add(GBDTNode.MAX_LEAF_VALUE + GBDTNode.MAX_VARIABLES*2 + getVariableIndex(setMembership.getTestValue(),context));
            values.add((double)setMembership.getSetValues().size());
            for (ExpressionNode setElementNode : setMembership.getSetValues())
                values.add(toValue(setElementNode));
        }
        else if (condition instanceof NotNode notNode) {  // handle if inversion: !(a >= b)
            if (notNode.children().size() == 1 && notNode.children().get(0) instanceof EmbracedNode embracedNode) {
                if (embracedNode.children().size() == 1 && isBinaryComparison(embracedNode.children().get(0))) {
                    OperationNode comparison = (OperationNode)embracedNode.children().get(0);
                    if (comparison.operators().get(0) == Operator.largerOrEqual)
                        values.add(GBDTNode.MAX_LEAF_VALUE + GBDTNode.MAX_VARIABLES*3 + getVariableIndex(comparison.children().get(0), context));
                    else
                        throw new IllegalArgumentException("Cannot optimize other conditions than >=, encountered: " + comparison.operators().get(0));
                    values.add(toValue(comparison.children().get(1)));
                }
            }
        }
        else {
            throw new IllegalArgumentException("Node condition could not be optimized: " + condition);
        }

        return values.size();
    }

    private boolean isBinaryComparison(ExpressionNode condition) {
        if ( ! (condition instanceof OperationNode binaryNode)) return false;
        if (binaryNode.operators().size() != 1) return false;
        if (binaryNode.operators().get(0) == Operator.largerOrEqual) return true;
        if (binaryNode.operators().get(0) == Operator.larger) return true;
        if (binaryNode.operators().get(0) == Operator.smallerOrEqual) return true;
        if (binaryNode.operators().get(0) == Operator.smaller) return true;
        if (binaryNode.operators().get(0) == Operator.approxEqual) return true;
        if (binaryNode.operators().get(0) == Operator.notEqual) return true;
        if (binaryNode.operators().get(0) == Operator.equal) return true;
        return false;
    }

    private double getVariableIndex(ExpressionNode node, ContextIndex context) {
        if (!(node instanceof ReferenceNode fNode)) {
            throw new IllegalArgumentException("Contained a left-hand comparison expression " +
                                               "which was not a feature value but was: " + node);
        }
        Integer index = context.getIndex(fNode.toString());
        if (index == null) {
            throw new IllegalStateException("The ranking expression contained feature '" + fNode.getName() +
                                            "', which is not known to " + context + ": The context must be created" +
                                            "from the same ranking expression which is to be optimized");
        }
        return index;
    }

    private double toValue(ExpressionNode node) {
        if (node instanceof ConstantNode) {
            Value value = ((ConstantNode)node).getValue();
            if (value instanceof DoubleCompatibleValue || value instanceof StringValue)
                return value.asDouble();
            else
                throw new IllegalArgumentException("Cannot optimize a node containing a value of type " +
                                                   value.getClass().getSimpleName() + " (" + value + ") in a set test: " + node);
        }

        if (node instanceof NegativeNode nNode) {
            if (!(nNode.getValue() instanceof ConstantNode)) {
                throw new IllegalArgumentException("Contained a negation of a non-number: " + nNode.getValue());
            }
            return -((ConstantNode)nNode.getValue()).getValue().asDouble();
        }
        throw new IllegalArgumentException("Node could not be optimized: " + node);
    }

    private double[] toArray(List<Double> valueList) {
        double[] valueArray = new double[valueList.size()];
        for (int i = 0; i < valueList.size(); i++) {
            valueArray[i] = valueList.get(i);
        }
        return valueArray;
    }

}
