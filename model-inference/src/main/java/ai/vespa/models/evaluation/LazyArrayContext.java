// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.AbstractArrayContext;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.ContextIndex;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.TensorType;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * An array context supporting functions invocations implemented as lazy values.
 *
 * @author bratseth
 */
class LazyArrayContext extends Context implements ContextIndex {

    /** The current values set */
    private Value[] values;

    private static DoubleValue constantZero = DoubleValue.frozen(0);

    /**
     * Create a fast lookup, lazy context for an expression.
     * This instance should be reused indefinitely by a single thread.
     *
     * @param expression the expression to create a context for
     */
    LazyArrayContext(RankingExpression expression, List<ExpressionFunction> functions) {
        values = new Value[doubleValues().length];
        Arrays.fill(values, DoubleValue.zero);
    }

    @Override
    protected void extractBindTargets(ExpressionNode node, Set<String> bindTargets) {
        if (isFunctionReference(node)) {
            ReferenceNode reference = (ReferenceNode)node;
            bindTargets.add(reference.getArguments().expressions().get(0).toString());

        }
        else {
            super.extractBindTargets(node, bindTargets);
        }
    }

    private boolean isFunctionReference(ExpressionNode node) {
        if ( ! (node instanceof ReferenceNode)) return false;

        ReferenceNode reference = (ReferenceNode)node;
        return reference.getName().equals("rankingExpression") && reference.getArguments().size() == 1;
    }

    /**
     * Puts a value by name.
     * The value will be frozen if it isn't already.
     *
     * @throws IllegalArgumentException if the name is not present in the ranking expression this was created with, and
     *         ignoredUnknownValues is false
     */
    @Override
    public final void put(String name, Value value) {
        Integer index = nameToIndex().get(name);
        if (index == null) {
            if (ignoreUnknownValues())
                return;
            else
                throw new IllegalArgumentException("Value '" + name + "' is not known to " + this);
        }
        put(index, value);
    }

    /** Same as put(index,DoubleValue.frozen(value)) */
    public final void put(int index, double value) {
        put(index, DoubleValue.frozen(value));
    }

    /**
     * Puts a value by index.
     * The value will be frozen if it isn't already.
     */
    public final void put(int index, Value value) {
        values[index] = value.freeze();
        try {
            doubleValues()[index] = value.asDouble();
        }
        catch (UnsupportedOperationException e) {
            doubleValues()[index] = Double.NaN; // see getDouble below
        }
    }

    @Override
    public TensorType getType(Reference reference) {
        Integer index = nameToIndex().get(reference.toString());
        if (index == null) return null;
        return values[index].type();
    }

    /** Perform a slow lookup by name */
    @Override
    public Value get(String name) {
        Integer index = nameToIndex().get(name);
        if (index == null) return DoubleValue.zero;
        return values[index];
    }

    /** Perform a fast lookup by index */
    @Override
    public final Value get(int index) {
        return values[index];
    }

    /** Perform a fast lookup directly of the value as a double. This is faster than get(index).asDouble() */
    @Override
    public final double getDouble(int index) {
        double value = doubleValues()[index];
        if (value == Double.NaN)
            throw new UnsupportedOperationException("Value at " + index + " has no double representation");
        return value;
    }

    /**
     * Creates a clone of this context suitable for evaluating against the same ranking expression
     * in a different thread (i.e, name name to index map, different value set.
     */
    public LazyArrayContext clone() {
        LazyArrayContext clone = (LazyArrayContext)super.clone();
        clone.values = new Value[nameToIndex().size()];
        Arrays.fill(values, constantZero);
        return clone;
    }

}
