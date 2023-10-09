// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.lang.MutableInteger;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.stream.CustomCollectors;

import java.util.BitSet;
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
public abstract class AbstractArrayContext extends Context implements Cloneable, ContextIndex {

    private final boolean ignoreUnknownValues;

    /** The name of the ranking expression this was created for */
    private final String rankingExpressionName;

    private IndexedBindings indexedBindings;

    /**
     * Create a fast lookup context for an expression.
     * This instance should be reused indefinitely by a single thread.
     * This will fail if unknown values are attempted added.
     */
    protected AbstractArrayContext(RankingExpression expression) {
        this(expression, false, defaultMissingValue);
    }

    protected AbstractArrayContext(RankingExpression expression, boolean ignoreUnknownValues) {
        this(expression, ignoreUnknownValues, defaultMissingValue);
    }

    /**
     * Create a fast lookup context for an expression.
     * This instance should be reused indefinitely by a single thread.
     *
     * @param expression the expression to create a context for
     * @param ignoreUnknownValues whether attempts to put values not present in this expression
     *                            should fail (false - the default), or be ignored (true)
     */
    protected AbstractArrayContext(RankingExpression expression, boolean ignoreUnknownValues, Value missingValue) {
        this.missingValue = missingValue.freeze();
        this.ignoreUnknownValues = ignoreUnknownValues;
        this.rankingExpressionName = expression.getName();
        this.indexedBindings = new IndexedBindings(expression, this.missingValue);
    }

    protected final Map<String, Integer> nameToIndex() { return indexedBindings.nameToIndex(); }
    protected final double[] doubleValues() { return indexedBindings.doubleValues(); }
    protected final boolean ignoreUnknownValues() { return ignoreUnknownValues; }

    @Override
    public Set<String> names() {
        return indexedBindings.names();
    }

    /**
     * Returns the index from a name.
     *
     * @throws NullPointerException is this name is not known to this context
     */
    @Override
    public final int getIndex(String name) { return indexedBindings.nameToIndex.get(name); }

    /** Returns the max number of variables which may be set in this */
    @Override
    public int size() { return indexedBindings.size(); }

    /** Perform a fast lookup directly of the value as a double. This is faster than get(index).asDouble() */
    @Override
    public double getDouble(int index) {
        return indexedBindings.getDouble(index);
    }

    final boolean isMissing(int index) {
        return indexedBindings.isMissing(index);
    }

    final void clearMissing(int index) {
        indexedBindings.clearMissing(index);
    }

    @Override
    public String toString() {
        return "fast lookup context for ranking expression '" + rankingExpressionName +
                "' [" + size() + " variables]";
    }

    /**
     * Creates a clone of this context suitable for evaluating against the same ranking expression
     * in a different thread (i.e, name name to index map, different value set.
     */
    @Override
    public AbstractArrayContext clone() {
        try {
            AbstractArrayContext clone = (AbstractArrayContext)super.clone();
            clone.indexedBindings = indexedBindings.clone();
            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Programming error");
        }
    }

    private static class IndexedBindings implements Cloneable {

        /** The mapping from variable name to index */
        private final Map<String, Integer> nameToIndex;

        /** The current values set, pre-converted to doubles */
        private double[] doubleValues;

        /** Which values actually are set */
        private BitSet setValues;

        /** Value to return if value is missing. */
        private final double missingValue;

        public IndexedBindings(RankingExpression expression, Value missingValue) {
            Set<String> bindTargets = new LinkedHashSet<>();
            extractBindTargets(expression.getRoot(), bindTargets);

            this.missingValue = missingValue.asDouble();
            setValues = new BitSet(bindTargets.size());
            doubleValues = new double[bindTargets.size()];
            for (int i = 0; i < bindTargets.size(); ++i) {
                doubleValues[i] = this.missingValue;
            }

            MutableInteger index = new MutableInteger(0);
            nameToIndex = bindTargets.stream().collect(CustomCollectors.toLinkedMap(name -> name, name -> index.next()));
        }

        private void extractBindTargets(ExpressionNode node, Set<String> bindTargets) {
            if (node instanceof ReferenceNode) {
                if (((ReferenceNode)node).getArguments().expressions().size() > 0)
                    throw new UnsupportedOperationException("Can not bind " + node +
                                                            ": Array lookup is not supported with features having arguments)");
                bindTargets.add(node.toString());
            }
            else if (node instanceof CompositeNode cNode) {
                for (ExpressionNode child : cNode.children())
                    extractBindTargets(child, bindTargets);
            }
        }

        public Map<String, Integer> nameToIndex() { return nameToIndex; }
        public double[] doubleValues() { return doubleValues; }

        public Set<String> names() { return nameToIndex.keySet(); }
        public int getIndex(String name) { return nameToIndex.get(name); }
        public int size() { return doubleValues.length; }
        public double getDouble(int index) { return doubleValues[index]; }
        public boolean isMissing(int index) { return ! setValues.get(index); }
        public void clearMissing(int index) { setValues.set(index); }

        /**
         * Creates a clone of this context suitable for evaluating against the same ranking expression
         * in a different thread (i.e, name name to index map, different value set.
         */
        @Override
        public IndexedBindings clone() {
            try {
                IndexedBindings clone = (IndexedBindings)super.clone();
                clone.setValues = new BitSet(nameToIndex.size());
                clone.doubleValues = new double[nameToIndex.size()];
                for (int i = 0; i < nameToIndex.size(); ++i) {
                    clone.doubleValues[i] = missingValue;
                }
                return clone;
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException("Programming error");
            }
        }

    }

}
