// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.ContextIndex;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An array context supporting functions invocations implemented as lazy values.
 *
 * @author bratseth
 */
public final class LazyArrayContext extends Context implements ContextIndex {

    private final ExpressionFunction function;
    private final IndexedBindings indexedBindings;

    private LazyArrayContext(ExpressionFunction function, IndexedBindings indexedBindings) {
        this.function = function;
        this.indexedBindings = indexedBindings.copy(this);
    }

    /** Create a fast lookup, lazy context for a function */
    LazyArrayContext(ExpressionFunction function,
                     Map<FunctionReference, ExpressionFunction> referencedFunctions,
                     List<Constant> constants,
                     Model model,
                     Value defaultFeatureValue) {
        this.function = function;
        this.indexedBindings = new IndexedBindings(function, referencedFunctions, constants, this, model, defaultFeatureValue);
    }

    /**
     * Sets the value to use for lookups to existing values which are not set in this context.
     * The default value that will be returned is NaN
     */
    public void setUnboundValue(Tensor value) {
        indexedBindings.setUnboundValue(value);
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
        return get(index).asDouble();
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

    /** Returns the (immutable) subset of names in this which must be bound when invoking */
    public Set<String> arguments() { return indexedBindings.arguments(); }

    private Integer requireIndexOf(String name) {
        Integer index = indexedBindings.indexOf(name);
        if (index == null)
            throw new IllegalArgumentException("Value '" + name + "' can not be bound in " + this);
        return index;
    }

    boolean isMissing(String name) {
        return indexedBindings.indexOf(name) == null;
    }

    /** Returns the value which should be used when no value is set */
    public Value defaultValue() {
        return indexedBindings.defaultValue;
    }

    /**
     * Creates a copy of this context suitable for evaluating against the same ranking expression
     * in a different thread or for re-binding free variables.
     */
    LazyArrayContext copy() {
        return new LazyArrayContext(function, indexedBindings);
    }

    private static class IndexedBindings {

        /** The mapping from variable name to index */
        private final ImmutableMap<String, Integer> nameToIndex;

        /** The names which needs to be bound externally when invoking this (i.e not constant or invocation */
        private final ImmutableSet<String> arguments;

        /** The current values set */
        private final Value[] values;

        /** The object instance which encodes "no value is set". The actual value of this is never used. */
        private static final Value missing = new DoubleValue(Double.NaN).freeze();

        /** The value to return for lookups where no value is set */
        private Value defaultValue;

        private IndexedBindings(ImmutableMap<String, Integer> nameToIndex,
                                Value[] values,
                                ImmutableSet<String> arguments,
                                Value defaultValue) {
            this.nameToIndex = nameToIndex;
            this.values = values;
            this.arguments = arguments;
            this.defaultValue = defaultValue.freeze();
        }

        /**
         * Creates indexed bindings for the given expressions.
         * The given expression and functions may be inspected but cannot be stored.
         */
        IndexedBindings(ExpressionFunction function,
                        Map<FunctionReference, ExpressionFunction> referencedFunctions,
                        List<Constant> constants,
                        LazyArrayContext owner,
                        Model model,
                        Value defaultFeatureValue) {
            // 1. Determine and prepare bind targets
            Set<String> bindTargets = new LinkedHashSet<>();
            Set<String> arguments = new LinkedHashSet<>(); // Arguments: Bind targets which need to be bound before invocation
            extractBindTargets(function.getBody().getRoot(), referencedFunctions, bindTargets, arguments);

            this.arguments = ImmutableSet.copyOf(arguments);
            this.defaultValue = defaultFeatureValue.freeze();
            values = new Value[bindTargets.size()];
            Arrays.fill(values, missing);

            int i = 0;
            ImmutableMap.Builder<String, Integer> nameToIndexBuilder = new ImmutableMap.Builder<>();
            for (String variable : bindTargets)
                nameToIndexBuilder.put(variable, i++);
            nameToIndex = nameToIndexBuilder.build();

            // 2. Bind the bind targets
            for (Constant constant : constants) {
                String constantReference = "constant(" + constant.name() + ")";
                Integer index = nameToIndex.get(constantReference);
                if (index != null) {
                    values[index] = new TensorValue(constant.value());
                }
            }

            for (Map.Entry<FunctionReference, ExpressionFunction> referencedFunction : referencedFunctions.entrySet()) {
                Integer index = nameToIndex.get(referencedFunction.getKey().serialForm());
                if (index != null) { // Referenced in this, so bind it
                    values[index] = new LazyValue(referencedFunction.getKey(), owner, model);
                }
            }
        }

        private void setUnboundValue(Tensor value) {
            defaultValue = new TensorValue(value).freeze();
        }

        private void extractBindTargets(ExpressionNode node,
                                        Map<FunctionReference, ExpressionFunction> functions,
                                        Set<String> bindTargets,
                                        Set<String> arguments) {
            if (isFunctionReference(node)) {
                FunctionReference reference = FunctionReference.fromSerial(node.toString()).get();
                bindTargets.add(reference.serialForm());

                extractBindTargets(functions.get(reference).getBody().getRoot(), functions, bindTargets, arguments);
            }
            else if (isConstant(node)) {
                bindTargets.add(node.toString());
            }
            else if (node instanceof ReferenceNode) {
                bindTargets.add(node.toString());
                arguments.add(node.toString());
            }
            else if (node instanceof CompositeNode) {
                CompositeNode cNode = (CompositeNode)node;
                for (ExpressionNode child : cNode.children())
                    extractBindTargets(child, functions, bindTargets, arguments);
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
            return reference.getName().equals("constant") && reference.getArguments().size() == 1;
        }

        Value get(int index) {
            Value value = values[index];
            return value == missing ? defaultValue : value;
        }

        void set(int index, Value value) {
            values[index] = value;
        }

        Set<String> names() { return nameToIndex.keySet(); }
        Set<String> arguments() { return arguments; }
        Integer indexOf(String name) { return nameToIndex.get(name); }

        IndexedBindings copy(Context context) {
            Value[] valueCopy = new Value[values.length];
            for (int i = 0; i < values.length; i++)
                valueCopy[i] = values[i] instanceof LazyValue ? ((LazyValue) values[i]).copyFor(context) : values[i];
            return new IndexedBindings(nameToIndex, valueCopy, arguments, defaultValue);
        }

    }

}
