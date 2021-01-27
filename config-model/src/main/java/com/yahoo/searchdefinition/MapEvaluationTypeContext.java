// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.expressiontransforms.OnnxModelTransformer;
import com.yahoo.searchdefinition.expressiontransforms.TokenTransformer;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.FunctionReferenceContext;
import com.yahoo.searchlib.rankingexpression.rule.NameNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A context which only contains type information.
 * This returns empty tensor types (double) for unknown features which are not
 * query, attribute or constant features, as we do not have information about which such
 * features exist (but we know those that exist are doubles).
 *
 * This is not multithread safe.
 *
 * @author bratseth
 */
public class MapEvaluationTypeContext extends FunctionReferenceContext implements TypeContext<Reference> {

    private final Map<Reference, TensorType> featureTypes = new HashMap<>();

    private final Map<Reference, TensorType> resolvedTypes = new HashMap<>();

    /** To avoid re-resolving diamond-shaped dependencies */
    private final Map<Reference, TensorType> globallyResolvedTypes;

    /** For invocation loop detection */
    private final Deque<Reference> currentResolutionCallStack;

    private final SortedSet<Reference> queryFeaturesNotDeclared;
    private boolean tensorsAreUsed;

    MapEvaluationTypeContext(Collection<ExpressionFunction> functions, Map<Reference, TensorType> featureTypes) {
        super(functions);
        this.featureTypes.putAll(featureTypes);
        this.currentResolutionCallStack =  new ArrayDeque<>();
        this.queryFeaturesNotDeclared = new TreeSet<>();
        tensorsAreUsed = false;
        globallyResolvedTypes = new HashMap<>();
    }

    private MapEvaluationTypeContext(Map<String, ExpressionFunction> functions,
                                     Map<String, String> bindings,
                                     Map<Reference, TensorType> featureTypes,
                                     Deque<Reference> currentResolutionCallStack,
                                     SortedSet<Reference> queryFeaturesNotDeclared,
                                     boolean tensorsAreUsed,
                                     Map<Reference, TensorType> globallyResolvedTypes) {
        super(functions, bindings);
        this.featureTypes.putAll(featureTypes);
        this.currentResolutionCallStack = currentResolutionCallStack;
        this.queryFeaturesNotDeclared = queryFeaturesNotDeclared;
        this.tensorsAreUsed = tensorsAreUsed;
        this.globallyResolvedTypes = globallyResolvedTypes;
    }

    public void setType(Reference reference, TensorType type) {
        featureTypes.put(reference, type);
        queryFeaturesNotDeclared.remove(reference);
    }

    @Override
    public TensorType getType(String reference) {
        throw new UnsupportedOperationException("Not able to parse general references from string form");
    }

    public void forgetResolvedTypes() {
        resolvedTypes.clear();
    }

    private boolean referenceCanBeResolvedGlobally(Reference reference) {
        Optional<ExpressionFunction> function = functionInvocation(reference);
        return function.isPresent() && function.get().arguments().size() == 0;
        // are there other cases we would like to resolve globally?
    }

    @Override
    public TensorType getType(Reference reference) {
        // computeIfAbsent without concurrent modification due to resolve adding more resolved entries:

        boolean canBeResolvedGlobally = referenceCanBeResolvedGlobally(reference);

        TensorType resolvedType = resolvedTypes.get(reference);
        if (resolvedType == null && canBeResolvedGlobally) {
            resolvedType = globallyResolvedTypes.get(reference);
        }
        if (resolvedType != null) {
            return resolvedType;
        }

        resolvedType = resolveType(reference);
        if (resolvedType == null)
            return defaultTypeOf(reference); // Don't store fallback to default as we may know more later
        resolvedTypes.put(reference, resolvedType);
        if (resolvedType.rank() > 0)
            tensorsAreUsed = true;

        if (canBeResolvedGlobally) {
            globallyResolvedTypes.put(reference, resolvedType);
        }

        return resolvedType;
    }

    private TensorType resolveType(Reference reference) {
        if (currentResolutionCallStack.contains(reference))
            throw new IllegalArgumentException("Invocation loop: " +
                                               currentResolutionCallStack.stream().map(Reference::toString).collect(Collectors.joining(" -> ")) +
                                               " -> " + reference);

        // Bound to a function argument, and not to a same-named identifier (which would lead to a loop)?
        Optional<String> binding = boundIdentifier(reference);
        if (binding.isPresent() && ! binding.get().equals(reference.toString())) {
            try {
                // This is not pretty, but changing to bind expressions rather
                // than their string values requires deeper changes
                return new RankingExpression(binding.get()).type(this);
            } catch (ParseException e) {
                throw new IllegalArgumentException(e);
            }
        }

        try {
            currentResolutionCallStack.addLast(reference);

            // A reference to an attribute, query or constant feature?
            if (FeatureNames.isSimpleFeature(reference)) {
                // The argument may be a local identifier bound to the actual value
                String argument = reference.simpleArgument().get();
                String argumentBinding = getBinding(argument);
                reference = Reference.simple(reference.name(), argumentBinding != null ? argumentBinding : argument);
                return featureTypes.get(reference);
            }

            // A reference to a function?
            Optional<ExpressionFunction> function = functionInvocation(reference);
            if (function.isPresent()) {
                return function.get().getBody().type(this.withBindings(bind(function.get().arguments(), reference.arguments())));
            }

            // A reference to an ONNX model?
            Optional<TensorType> onnxFeatureType = onnxFeatureType(reference);
            if (onnxFeatureType.isPresent()) {
                return onnxFeatureType.get();
            }

            // A reference to a feature for transformer token input?
            Optional<TensorType> transformerTokensFeatureType = transformerTokensFeatureType(reference);
            if (transformerTokensFeatureType.isPresent()) {
                return transformerTokensFeatureType.get();
            }

            // A reference to a feature which returns a tensor?
            Optional<TensorType> featureTensorType = tensorFeatureType(reference);
            if (featureTensorType.isPresent()) {
                return featureTensorType.get();
            }

            // A directly injected identifier? (Useful for stateless model evaluation)
            if (reference.isIdentifier() && featureTypes.containsKey(reference)) {
                return featureTypes.get(reference);
            }

            // We do not know what this is - since we do not have complete knowledge about the match features
            // in Java we must assume this is a match feature and return the double type - which is the type of
            // all match features
            return TensorType.empty;
        }
        finally {
            currentResolutionCallStack.removeLast();
        }
    }

    /**
     * Returns the default type for this simple feature, or null if it does not have a default
     */
    public TensorType defaultTypeOf(Reference reference) {
        if ( ! FeatureNames.isSimpleFeature(reference))
            throw new IllegalArgumentException("This can only be called for simple references, not " + reference);
        if (reference.name().equals("query")) { // we do not require all query features to be declared, only non-doubles
            queryFeaturesNotDeclared.add(reference);
            return TensorType.empty;
        }
        return null;
    }

    /**
     * Returns the binding if this reference is a simple identifier which is bound in this context.
     * Returns empty otherwise.
     */
    private Optional<String> boundIdentifier(Reference reference) {
        if ( ! reference.arguments().isEmpty()) return Optional.empty();
        if ( reference.output() != null) return Optional.empty();
        return Optional.ofNullable(getBinding(reference.name()));
    }

    private Optional<ExpressionFunction> functionInvocation(Reference reference) {
        if (reference.output() != null) return Optional.empty();
        ExpressionFunction function = functions().get(reference.name());
        if (function == null) return Optional.empty();
        if (function.arguments().size() != reference.arguments().size()) return Optional.empty();
        return Optional.of(function);
    }

    private Optional<TensorType> onnxFeatureType(Reference reference) {
        if ( ! reference.name().equals("onnxModel") && ! reference.name().equals("onnx"))
            return Optional.empty();

        if ( ! featureTypes.containsKey(reference)) {
            String configOrFileName = reference.arguments().expressions().get(0).toString();

            // Look up standardized format as added in RankProfile
            String modelConfigName = OnnxModelTransformer.getModelConfigName(reference);
            String modelOutput = OnnxModelTransformer.getModelOutput(reference, null);

            reference  = new Reference("onnxModel", new Arguments(new ReferenceNode(modelConfigName)), modelOutput);
            if ( ! featureTypes.containsKey(reference)) {
                throw new IllegalArgumentException("Missing onnx-model config for '" + configOrFileName + "'");
            }
        }

        return Optional.of(featureTypes.get(reference));
    }

    private Optional<TensorType> transformerTokensFeatureType(Reference reference) {
        if ( ! reference.name().equals("tokenTypeIds") &&
                ! reference.name().equals("tokenInputIds") &&
                ! reference.name().equals("tokenAttentionMask"))
            return Optional.empty();

        if ( ! (reference.arguments().size() > 1))
            throw new IllegalArgumentException(reference.name() + " must have at least 2 arguments");

        ExpressionNode size = reference.arguments().expressions().get(0);
        return Optional.of(TokenTransformer.createTensorType(reference.name(), size));
    }

    /**
     * There are two features which returns the (non-empty) tensor type: tensorFromLabels and tensorFromWeightedSet.
     * This returns the type of those features if this is a reference to either of them, or empty otherwise.
     */
    private Optional<TensorType> tensorFeatureType(Reference reference) {
        if ( ! reference.name().equals("tensorFromLabels") && ! reference.name().equals("tensorFromWeightedSet"))
            return Optional.empty();

        if (reference.arguments().size() != 1 && reference.arguments().size() != 2)
            throw new IllegalArgumentException(reference.name() + " must have one or two arguments");

        ExpressionNode arg0 = reference.arguments().expressions().get(0);
        if ( ! ( arg0 instanceof ReferenceNode) || ! FeatureNames.isSimpleFeature(((ReferenceNode)arg0).reference()))
            throw new IllegalArgumentException("The first argument of " + reference.name() +
                                               " must be a simple feature, not " + arg0);

        String dimension;
        if (reference.arguments().size() > 1) {
            ExpressionNode arg1 = reference.arguments().expressions().get(1);
            if ( ( ! (arg1 instanceof ReferenceNode) || ! (((ReferenceNode)arg1).reference().isIdentifier()))
                 &&
                 ( ! (arg1 instanceof NameNode)))
                throw new IllegalArgumentException("The second argument of " + reference.name() +
                                                   " must be a dimension name, not " + arg1);
            dimension = reference.arguments().expressions().get(1).toString();
        }
        else { // default
            dimension = ((ReferenceNode)arg0).reference().arguments().expressions().get(0).toString();
        }

        // TODO: Determine the type of the weighted set/vector and use that as value type
        return Optional.of(new TensorType.Builder().mapped(dimension).build());
    }

    /** Binds the given list of formal arguments to their actual values */
    private Map<String, String> bind(List<String> formalArguments,
                                     Arguments invocationArguments) {
        Map<String, String> bindings = new HashMap<>(formalArguments.size());
        for (int i = 0; i < formalArguments.size(); i++) {
            String identifier = invocationArguments.expressions().get(i).toString();
            String identifierBinding = super.getBinding(identifier);
            bindings.put(formalArguments.get(i), identifierBinding != null ? identifierBinding : identifier);
        }
        return bindings;
    }

    public Map<Reference, TensorType> featureTypes() {
        return Collections.unmodifiableMap(featureTypes);
    }

    /**
     * Returns an unmodifiable view of the query features which was requested but for which we have no type info
     * (such that they default to TensorType.empty), shared between all instances of this
     * involved in resolving a particular rank profile.
     */
    public SortedSet<Reference> queryFeaturesNotDeclared() {
        return Collections.unmodifiableSortedSet(queryFeaturesNotDeclared);
    }

    /** Returns true if any feature across all instances involved in resolving this rank profile resolves to a tensor */
    public boolean tensorsAreUsed() { return tensorsAreUsed; }

    @Override
    public MapEvaluationTypeContext withBindings(Map<String, String> bindings) {
        return new MapEvaluationTypeContext(functions(),
                                            bindings,
                                            featureTypes,
                                            currentResolutionCallStack,
                                            queryFeaturesNotDeclared,
                                            tensorsAreUsed,
                                            globallyResolvedTypes);
    }

}
