// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.google.common.collect.ImmutableMap;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.ContextIndex;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.TensorType;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * An array context supporting functions invocations implemented as lazy values.
 *
 * @author bratseth
 */
final class LazyArrayContext extends Context implements ContextIndex {

    private final IndexedBindings indexedBindings;

    private LazyArrayContext(IndexedBindings indexedBindings) {
        this.indexedBindings = indexedBindings.copy(this);
    }

    /**
     * Create a fast lookup, lazy context for an expression.
     *
     * @param expression the expression to create a context for
     */
    LazyArrayContext(RankingExpression expression, Map<String, ExpressionFunction> functions, Model model) {
        this.indexedBindings = new IndexedBindings(expression, functions, this, model);
    }

    /**
     * Puts a value by name.
     * The value will be frozen if it isn't already.
     *
     * @throws IllegalArgumentException if the name is not present in the ranking expression this was created with, and
     *         ignoredUnknownValues is false
     */
    @Override
    public void put(String name, Value value) {
        put(requireIndexOf(name), value);
    }

    /** Same as put(index,DoubleValue.frozen(value)) */
    public final void put(int index, double value) {
        put(index, DoubleValue.frozen(value));
    }

    /**
     * Puts a value by index.
     * The value will be frozen if it isn't already.
     */
    public void put(int index, Value value) {
        indexedBindings.set(index, value.freeze());
    }

    @Override
    public TensorType getType(Reference reference) {
        // TODO: Add type information so we do not need to evaluate to get this
        return get(requireIndexOf(reference.toString())).type();
    }

    /** Perform a slow lookup by name */
    @Override
    public Value get(String name) {
        return get(requireIndexOf(name));
    }

    /** Perform a fast lookup by index */
    @Override
    public Value get(int index) {
        return indexedBindings.get(index);
    }

    @Override
    public double getDouble(int index) {
        double value = get(index).asDouble();
        if (value == Double.NaN)
            throw new UnsupportedOperationException("Value at " + index + " has no double representation");
        return value;
    }

    @Override
    public int getIndex(String name) {
        return requireIndexOf(name);
    }

    @Override
    public int size() {
        return indexedBindings.names().size();
    }

    @Override
    public Set<String> names() { return indexedBindings.names(); }

    private Integer requireIndexOf(String name) {
        Integer index = indexedBindings.indexOf(name);
        if (index == null)
            throw new IllegalArgumentException("Value '" + name + "' can not be bound in " + this);
        return index;
    }

    /**
     * Creates a copy of this context suitable for evaluating against the same ranking expression
     * in a different thread or for re-binding free variables.
     */
    LazyArrayContext copy() {
        return new LazyArrayContext(indexedBindings);
    }

    private static class IndexedBindings {

        /** The mapping from variable name to index */
        private final ImmutableMap<String, Integer> nameToIndex;

        /** The current values set, pre-converted to doubles */
        private final Value[] values;

        private IndexedBindings(ImmutableMap<String, Integer> nameToIndex, Value[] values) {
            this.nameToIndex = nameToIndex;
            this.values = values;
        }

        /**
         * Creates indexed bindings for the given expressions.
         * The given expression and functions may be inspected but cannot be stored.
         */
        IndexedBindings(RankingExpression expression,
                        Map<String, ExpressionFunction> functions,
                        LazyArrayContext owner,
                        Model model) {
            Set<String> bindTargets = new LinkedHashSet<>();
            extractBindTargets(expression.getRoot(), functions, bindTargets);

            values = new Value[bindTargets.size()];
            Arrays.fill(values, DoubleValue.zero);

            int i = 0;
            ImmutableMap.Builder<String, Integer> nameToIndexBuilder = new ImmutableMap.Builder<>();
            for (String variable : bindTargets)
                nameToIndexBuilder.put(variable,i++);
            nameToIndex = nameToIndexBuilder.build();

            for (Map.Entry<String, ExpressionFunction> function : functions.entrySet()) {
                Integer index = nameToIndex.get(function.getKey());
                if (index != null) // Referenced in this, so bind it
                    values[index] = new LazyValue(function.getKey(), owner, model);
            }
        }

        private void extractBindTargets(ExpressionNode node, Map<String, ExpressionFunction> functions, Set<String> bindTargets) {
            if (isFunctionReference(node)) {
                String reference = node.toString();
                bindTargets.add(reference);

                extractBindTargets(functions.get(reference).getBody().getRoot(), functions, bindTargets);
            }
            else if (isConstant(node)) {
                // Ignore
            }
            else if (node instanceof ReferenceNode) {
                bindTargets.add(node.toString());
            }
            else if (node instanceof CompositeNode) {
                CompositeNode cNode = (CompositeNode)node;
                for (ExpressionNode child : cNode.children())
                    extractBindTargets(child, functions, bindTargets);
            }
        }

        private boolean isFunctionReference(ExpressionNode node) {
            if ( ! (node instanceof ReferenceNode)) return false;

            ReferenceNode reference = (ReferenceNode)node;
            return reference.getName().equals("rankingExpression") && reference.getArguments().size() == 1;
        }

        private boolean isConstant(ExpressionNode node) {
            if ( ! (node instanceof ReferenceNode)) return false;

            ReferenceNode reference = (ReferenceNode)node;
            return reference.getName().equals("value") && reference.getArguments().size() == 1;
        }

        Value get(int index) { return values[index]; }
        void set(int index, Value value) { values[index] = value; }
        Set<String> names() { return nameToIndex.keySet(); }
        Integer indexOf(String name) { return nameToIndex.get(name); }

        IndexedBindings copy(Context context) {
            Value[] valueCopy = new Value[values.length];
            for (int i = 0; i < values.length; i++)
                valueCopy[i] = values[i] instanceof LazyValue ? ((LazyValue)values[i]).copyFor(context) : values[i];
            return new IndexedBindings(nameToIndex, valueCopy);
        }

    }

}
