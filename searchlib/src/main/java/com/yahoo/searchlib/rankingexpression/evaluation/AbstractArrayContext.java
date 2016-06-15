// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.google.common.collect.ImmutableMap;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Superclass of contexts which supports array index based lookup.
 * Instances may be reused indefinitely for evaluations of a single
 * ranking expression, in a single thread at the time.
 *
 * @author bratseth
 */
public abstract class AbstractArrayContext extends Context implements Cloneable {

    private final boolean ignoreUnknownValues;

    /** The mapping from variable name to index */
    private final ImmutableMap<String, Integer> nameToIndex;

    /** The current values set, pre-converted to doubles */
    private double[] doubleValues;

    /** The name of the ranking expression this was created for */
    private final String rankingExpressionName;

    /**
     * Create a fast lookup context for an expression.
     * This instance should be reused indefinitely by a single thread.
     * This will fail if unknown values are attempted added.
     */
    protected AbstractArrayContext(RankingExpression expression) {
        this(expression, false);
    }

    /**
     * Create a fast lookup context for an expression.
     * This instance should be reused indefinitely by a single thread.
     *
     * @param expression the expression to create a context for
     * @param ignoreUnknownValues whether attempts to put values not present in this expression
     *                            should fail (false - the default), or be ignored (true)
     */
    protected AbstractArrayContext(RankingExpression expression, boolean ignoreUnknownValues) {
        this.ignoreUnknownValues = ignoreUnknownValues;
        this.rankingExpressionName = expression.getName();
        Set<String> variables = new LinkedHashSet<>();
        extractVariables(expression.getRoot(),variables);

        doubleValues = new double[variables.size()];

        int i = 0;
        ImmutableMap.Builder<String, Integer> nameToIndexBuilder = new ImmutableMap.Builder<>();
        for (String variable : variables)
            nameToIndexBuilder.put(variable,i++);
        nameToIndex = nameToIndexBuilder.build();
    }

    private void extractVariables(ExpressionNode node,Set<String> variables) {
        if (node instanceof ReferenceNode) {
            ReferenceNode fNode=(ReferenceNode)node;
            if (fNode.getArguments().expressions().size()>0)
                throw new UnsupportedOperationException("Array lookup is not supported with features having arguments)");
            variables.add(fNode.toString());
        }
        else if (node instanceof CompositeNode) {
            CompositeNode cNode=(CompositeNode)node;
            for (ExpressionNode child : cNode.children())
                extractVariables(child,variables);
        }
    }

    protected final Map<String, Integer> nameToIndex() { return nameToIndex; }
    protected final double[] doubleValues() { return doubleValues; }
    protected final boolean ignoreUnknownValues() { return ignoreUnknownValues; }

    /**
     * Creates a clone of this context suitable for evaluating against the same ranking expression
     * in a different thread (i.e, name name to index map, different value set.
     */
    public AbstractArrayContext clone() {
        try {
            AbstractArrayContext clone=(AbstractArrayContext)super.clone();
            clone.doubleValues=new double[nameToIndex.size()];
            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Programming error");
        }
    }

    public Set<String> names() {
        return nameToIndex.keySet();
    }

    /**
     * Returns the index from a name.
     *
     * @throws NullPointerException is this name is not known to this context
     */
    public final int getIndex(String name) {
        return nameToIndex.get(name);
    }

    /** Returns the max number of variables which may be set in this */
    public int size() {
        return doubleValues.length;
    }

    /** Perform a fast lookup directly of the value as a double. This is faster than get(index).asDouble() */
    @Override
    public double getDouble(int index) {
        return doubleValues[index];
    }

    @Override
    public String toString() {
        return "fast lookup context for ranking expression '" + rankingExpressionName +
                "' [" + doubleValues.length + " variables]";
    }

}
