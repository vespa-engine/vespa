// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                     List<OnnxModel> onnxModels,
                     Model model) {
        this.function = function;
        this.indexedBindings = new IndexedBindings(function, referencedFunctions, constants, onnxModels, this, model);
    }

    /**
     * Sets the value to use for lookups to existing values which are not set in this context.
     * The default value that will be returned is NaN
     */
    public void setMissingValue(Tensor value) {
        indexedBindings.setMissingValue(value);
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

    /** Returns the set of ONNX models that need to be evaluated on this context */
    public Map<String, OnnxModel> onnxModels() { return indexedBindings.onnxModels(); }

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
        return indexedBindings.missingValue;
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

        /** ONNX models indexed by rank feature that calls them */
        private final ImmutableMap<String, OnnxModel> onnxModels;

        /** The object instance which encodes "no value is set". The actual value of this is never used. */
        private static final Value missing = new DoubleValue(Double.NaN).freeze();

        /** The value to return for lookups where no value is set (default: NaN) */
        private Value missingValue = new DoubleValue(Double.NaN).freeze();


        private IndexedBindings(ImmutableMap<String, Integer> nameToIndex,
                                Value[] values,
                                ImmutableSet<String> arguments,
                                ImmutableMap<String, OnnxModel> onnxModels) {
            this.nameToIndex = nameToIndex;
            this.values = values;
            this.arguments = arguments;
            this.onnxModels = onnxModels;
        }

        /**
         * Creates indexed bindings for the given expressions.
         * The given expression and functions may be inspected but cannot be stored.
         */
        IndexedBindings(ExpressionFunction function,
                        Map<FunctionReference, ExpressionFunction> referencedFunctions,
                        List<Constant> constants,
                        List<OnnxModel> onnxModels,
                        LazyArrayContext owner,
                        Model model) {
            // 1. Determine and prepare bind targets
            Set<String> bindTargets = new LinkedHashSet<>();
            Set<String> arguments = new LinkedHashSet<>(); // Arguments: Bind targets which need to be bound before invocation
            Map<String, OnnxModel> onnxModelsInUse = new HashMap<>();
            extractBindTargets(function.getBody().getRoot(), referencedFunctions, bindTargets, arguments, onnxModels, onnxModelsInUse);

            this.onnxModels = ImmutableMap.copyOf(onnxModelsInUse);
            this.arguments = ImmutableSet.copyOf(arguments);
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

        private void setMissingValue(Tensor value) {
            missingValue = new TensorValue(value).freeze();
        }

        private void extractBindTargets(ExpressionNode node,
                                        Map<FunctionReference, ExpressionFunction> functions,
                                        Set<String> bindTargets,
                                        Set<String> arguments,
                                        List<OnnxModel> onnxModels,
                                        Map<String, OnnxModel> onnxModelsInUse) {
            if (isFunctionReference(node)) {
                FunctionReference reference = FunctionReference.fromSerial(node.toString()).get();
                bindTargets.add(reference.serialForm());

                ExpressionFunction function = functions.get(reference);
                if (function == null) return; // Function not included in this model: Not all models are for standalone use
                ExpressionNode functionNode = function.getBody().getRoot();
                extractBindTargets(functionNode, functions, bindTargets, arguments, onnxModels, onnxModelsInUse);
            }
            else if (isOnnx(node)) {
                extractOnnxTargets(node, bindTargets, arguments, onnxModels, onnxModelsInUse);
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
                    extractBindTargets(child, functions, bindTargets, arguments, onnxModels, onnxModelsInUse);
            }
        }

        /**
         * Extract the feature used to evaluate the onnx model. e.g. onnx(name) and add
         * that as a bind target and argument. During evaluation, this will be evaluated before
         * the rest of the expression and the result is added to the context. Also extract the
         * inputs to the model and add them as bind targets and arguments.
         */
        private void extractOnnxTargets(ExpressionNode node,
                                        Set<String> bindTargets,
                                        Set<String> arguments,
                                        List<OnnxModel> onnxModels,
                                        Map<String, OnnxModel> onnxModelsInUse) {
            Optional<String> modelName = getArgument(node);
            if (modelName.isPresent()) {
                for (OnnxModel onnxModel : onnxModels) {
                    if (onnxModel.name().equals(modelName.get())) {
                        String onnxFeature = node.toString();
                        bindTargets.add(onnxFeature);

                        // Load the model (if not already loaded) to extract inputs
                        onnxModel.load();

                        for(String input : onnxModel.inputs().keySet()) {
                            bindTargets.add(input);
                            arguments.add(input);
                        }
                        onnxModelsInUse.put(onnxFeature, onnxModel);
                    }
                }
            }
        }

        private Optional<String> getArgument(ExpressionNode node) {
            if (node instanceof ReferenceNode) {
                ReferenceNode reference = (ReferenceNode) node;
                if (reference.getArguments().size() > 0) {
                    if (reference.getArguments().expressions().get(0) instanceof ConstantNode) {
                        ExpressionNode constantNode = reference.getArguments().expressions().get(0);
                        return Optional.of(stripQuotes(constantNode.toString()));
                    }
                    if (reference.getArguments().expressions().get(0) instanceof ReferenceNode) {
                        ReferenceNode referenceNode = (ReferenceNode) reference.getArguments().expressions().get(0);
                        return Optional.of(referenceNode.getName());
                    }
                }
            }
            return Optional.empty();
        }

        public static String stripQuotes(String s) {
            if (s.codePointAt(0) == '"' && s.codePointAt(s.length()-1) == '"')
                return s.substring(1, s.length()-1);
            if (s.codePointAt(0) == '\'' && s.codePointAt(s.length()-1) == '\'')
                return s.substring(1, s.length()-1);
            return s;
        }

        private boolean isFunctionReference(ExpressionNode node) {
            if ( ! (node instanceof ReferenceNode)) return false;
            ReferenceNode reference = (ReferenceNode)node;
            return reference.getName().equals("rankingExpression") && reference.getArguments().size() == 1;
        }

        private boolean isOnnx(ExpressionNode node) {
            if ( ! (node instanceof ReferenceNode)) return false;
            ReferenceNode reference = (ReferenceNode) node;
            return reference.getName().equals("onnx") || reference.getName().equals("onnxModel");
        }

        private boolean isConstant(ExpressionNode node) {
            if ( ! (node instanceof ReferenceNode)) return false;
            ReferenceNode reference = (ReferenceNode)node;
            return reference.getName().equals("constant") && reference.getArguments().size() == 1;
        }

        Value get(int index) {
            Value value = values[index];
            return value == missing ? missingValue : value;
        }

        void set(int index, Value value) {
            values[index] = value;
        }

        Set<String> names() { return nameToIndex.keySet(); }
        Set<String> arguments() { return arguments; }
        Integer indexOf(String name) { return nameToIndex.get(name); }
        Map<String, OnnxModel> onnxModels() { return onnxModels; }

        IndexedBindings copy(Context context) {
            Value[] valueCopy = new Value[values.length];
            for (int i = 0; i < values.length; i++)
                valueCopy[i] = values[i] instanceof LazyValue ? ((LazyValue) values[i]).copyFor(context) : values[i];
            return new IndexedBindings(nameToIndex, valueCopy, arguments, onnxModels);
        }

    }

}
