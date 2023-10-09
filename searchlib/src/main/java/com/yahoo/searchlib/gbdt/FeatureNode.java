// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import com.yahoo.searchlib.rankingexpression.evaluation.StringValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.List;
import java.util.Optional;

/**
 * A node in a GBDT tree which references a feature value
 *
 * @author bratseth
 */
public abstract class FeatureNode extends TreeNode {

    private final String feature;

    private final TreeNode left;
    private final TreeNode right;

    public FeatureNode(String feature, Optional<Integer> samples, TreeNode left, TreeNode right) {
        super(samples);
        this.feature = feature;
        this.left = left;
        this.right = right;
    }

    public String feature() { return feature; }

    public TreeNode left() { return left; }

    public TreeNode right() { return right; }

    // TODO: Integrate with programmatic API rather than strings
    @Override
    public String toRankingExpression() {
        StringBuilder expression = new StringBuilder();
        expression.append("if (").append(feature).append(rankingExpressionCondition());
        expression.append(", ").append(left.toRankingExpression());
        expression.append(", ").append(right.toRankingExpression());

        Optional<Float> trueProbability = trueProbability();
        if (trueProbability.isPresent())
            expression.append(", ").append(trueProbability.get());

        expression.append(")");
        return expression.toString();
    }

    private Optional<Float> trueProbability() {
        if (left.samples().isPresent() && right.samples().isPresent())
            return Optional.of((float)left.samples().get() / (left.samples().get() + right.samples().get()));
        return Optional.empty();
    }

    protected abstract String rankingExpressionCondition();

    public static FeatureNode fromDom(Node node) {
        List<Element> children = XmlHelper.getChildElements(node, null);
        if (children.size() != 2) {
            throw new IllegalArgumentException("Expected 2 children in element '" + node.getNodeName() + "', got " +
                                               children.size() + ".");
        }

        String name = XmlHelper.getAttributeText(node, "feature");
        Value[] values = toValues(XmlHelper.getAttributeText(node, "value"));
        Optional<Integer> samples = toInteger(XmlHelper.getOptionalAttributeText(node, "nSamples"));
        TreeNode left = TreeNode.fromDom(children.get(0));
        TreeNode right = TreeNode.fromDom(children.get(1));

        if (name.endsWith("$") || values.length>1 || values[0] instanceof StringValue)
            return new CategoryFeatureNode(name, values, samples, left, right);
        else
            return new NumericFeatureNode(name, values[0], samples, left, right);
    }

    /** Converts one or more comma-separated values into an array of values */
    private static Value[] toValues(String valueListString) {
        String[] valueStrings = valueListString.split(",");
        Value[] values = new Value[valueStrings.length];
        for (int i=0; i<valueStrings.length; i++) {
            try {
                values[i] = Value.parse(valueStrings[i]);
            }
            catch (NumberFormatException e) { // allow un(double)quoted string values in Gbdt XML trees
                values[i] = new StringValue(valueStrings[i]);
            }
        }
        return values;
    }

}
