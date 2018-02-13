// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.EvaluationTypeContext;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.FunctionReferenceContext;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.MapEvaluationContext;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A context which only contains type information.
 * This returns empty tensor types (double) for unknown features which are not
 * query, attribute or constant features, as we do not have information about which such
 * features exist (but we know those that exist are doubles).
 *
 * @author bratseth
 */
public class MapEvaluationTypeContext extends FunctionReferenceContext implements EvaluationTypeContext {

    private final Map<String, TensorType> featureTypes = new HashMap<>();

    public MapEvaluationTypeContext(Collection<ExpressionFunction> functions) {
        super(functions);
    }

    public MapEvaluationTypeContext(Map<String, ExpressionFunction> functions,
                                    Map<String, String> bindings,
                                    Map<String, TensorType> featureTypes) {
        super(functions, bindings);
        this.featureTypes.putAll(featureTypes);
    }

    public void setType(String name, TensorType type) {
        featureTypes.put(FeatureNames.canonicalize(name), type);
    }

    // TODO: Remove?
    @Override
    public TensorType getType(String name) {
        if (FeatureNames.isFeature(name))
            return featureTypes.get(FeatureNames.canonicalize(name));
        else
            return TensorType.empty; // we do not have type information for these. Correct would be either empty or null
    }

    @Override
    public TensorType getType(String name, Arguments arguments, String output) {
        Optional<String> simpleFeature = simpleFeature(name, arguments); // (all simple feature outputs return the same type)
        if (simpleFeature.isPresent())
            return featureTypes.get(simpleFeature.get());

        Optional<ExpressionFunction> function = functionInvocation(name, output);
        if (function.isPresent())
            return function.get().getBody().type(this.withBindings(bind(function.get().arguments())));

        // We do not know what this is - since we do not have complete knowledge abut the match features
        // in Java we must assume this is a match feature and return the double type - which is the type of all
        // all match features
        return TensorType.empty;
    }

    /**
     * If the arguments makes a simple feature ("attribute(name)", "constant(name)" or "query(name)",
     * it is returned. Otherwise empty is returned.
     */
    private Optional<String> simpleFeature(String name, Arguments arguments) {
        Optional<String> argument = simpleArgument(arguments);
        if ( ! argument.isPresent()) return Optional.empty();

        // The argument may be a "local value" bound to another value, or else it is the "global" argument of the feature
        String actualArgument = bindings.getOrDefault(argument.get(), argument.get());

        String feature = asFeatureString(name, actualArgument);
        if (FeatureNames.isFeature(feature))
            return Optional.of(feature);
        else
            return Optional.empty();
    }

    private String asFeatureString(String name, String argument) {
        return name + "(" + argument + ")";
    }

    /**
     * If these arguments contains one simple argument string, it is returned.
     * Otherwise null is returned.
     */
    private Optional<String> simpleArgument(Arguments arguments) {
        if (arguments.expressions().size() != 1) return Optional.empty();
        ExpressionNode argument = arguments.expressions().get(0);

        if ( ! (argument instanceof ReferenceNode)) return Optional.empty();
        ReferenceNode refArgument = (ReferenceNode)argument;

        if ( ! refArgument.isBindableName()) return Optional.empty();

        return Optional.of(refArgument.getName());
    }

    private Optional<ExpressionFunction> functionInvocation(String name, String output) {
        if (output != null) return Optional.empty();
        return Optional.ofNullable(functions().get(name));
    }

    /** Binds the given list of formal arguments to their actual values */
    private Map<String, String> bind(List<String> arguments) {
        Map<String, String> bindings = new HashMap<>(arguments.size());
        for (String formalArgument : arguments)
            bindings.put(formalArgument, null); // TODO
        return bindings;
    }

    public Map<String, TensorType> featureTypes() { return Collections.unmodifiableMap(featureTypes); }

    @Override
    public MapEvaluationTypeContext withBindings(Map<String, String> bindings) {
        if (bindings.isEmpty() && this.bindings.isEmpty()) return this;
        return new MapEvaluationTypeContext(functions(), bindings, featureTypes);
    }

}
