// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.FunctionReferenceContext;
import com.yahoo.searchlib.rankingexpression.rule.NameNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.TensorType;
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
public class MapEvaluationTypeContext extends FunctionReferenceContext implements TypeContext {

    private final Map<ReferenceNode.Reference, TensorType> featureTypes = new HashMap<>();

    public MapEvaluationTypeContext(Collection<ExpressionFunction> functions) {
        super(functions);
    }

    public MapEvaluationTypeContext(Map<String, ExpressionFunction> functions,
                                    Map<String, String> bindings,
                                    Map<ReferenceNode.Reference, TensorType> featureTypes) {
        super(functions, bindings);
        this.featureTypes.putAll(featureTypes);
    }

    public void setType(Name name, TensorType type) {
        // TODO: Use a type parameter if we do this both here and in getType ...
        if ( ! (name instanceof ReferenceNode.Reference))
            throw new IllegalArgumentException("Not expecting unstructured names here");
        featureTypes.put((ReferenceNode.Reference)name, type);
    }

    @Override
    public TensorType getType(Name name) {
        if ( ! (name instanceof ReferenceNode.Reference))
            throw new IllegalArgumentException("Not expecting unstructured names here");
        ReferenceNode.Reference reference = (ReferenceNode.Reference)name;

        Optional<String> binding = boundIdentifier(reference);
        if (binding.isPresent()) {
            try {
                // This is not pretty, but changing to bind expressions rather
                // than their string values requires deeper changes
                return new RankingExpression(binding.get()).type(this);
            }
            catch (ParseException e) {
                throw new IllegalArgumentException(e);
            }
        }

        if (isSimpleFeature(reference)) {
            // The argument may be a local identifier bound to the actual value
            String argument = simpleArgument(reference.arguments()).get();
            reference = ReferenceNode.Reference.simple(reference.name(), bindings.getOrDefault(argument, argument));
            return featureTypes.get(reference);
        }

        Optional<ExpressionFunction> function = functionInvocation(reference);
        if (function.isPresent()) {
            return function.get().getBody().type(this.withBindings(bind(function.get().arguments(), reference.arguments())));
        }

        // We do not know what this is - since we do not have complete knowledge abut the match features
        // in Java we must assume this is a match feature and return the double type - which is the type of all
        // all match features
        return TensorType.empty;
    }

    /**
     * Returns the binding if this reference is a simple identifier which is bound in this context.
     * Returns empty otherwise.
     */
    private Optional<String> boundIdentifier(ReferenceNode.Reference reference) {
        if ( ! reference.arguments().isEmpty()) return Optional.empty();
        if ( reference.output() != null) return Optional.empty();
        return Optional.ofNullable(bindings.get(reference.name()));
    }

    /**
     * Return whether the reference (discarding the output) is a simple feature
     * ("attribute(name)", "constant(name)" or "query(name)").
     * We disregard the output because all outputs under a simple feature have the same type.
     */
    private boolean isSimpleFeature(ReferenceNode.Reference reference) {
        Optional<String> argument = simpleArgument(reference.arguments());
        if ( ! argument.isPresent()) return false;
        return reference.name().equals("attribute") ||
               reference.name().equals("constant") ||
               reference.name().equals("query");
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

        if ( ! refArgument.reference().isIdentifier()) return Optional.empty();

        return Optional.of(refArgument.getName());
    }

    private Optional<ExpressionFunction> functionInvocation(ReferenceNode.Reference reference) {
        if (reference.output() != null) return Optional.empty();
        return Optional.ofNullable(functions().get(reference.name()));
    }

    /** Binds the given list of formal arguments to their actual values */
    private Map<String, String> bind(List<String> formalArguments,
                                     Arguments invocationArguments) {
        // TODO: What is our position on argument overloading/argument count differences?
        Map<String, String> bindings = new HashMap<>(formalArguments.size());
        for (int i = 0; i < formalArguments.size(); i++) {
            // ensureIsIdentifier(invocationArguments.expressions().get(i),
            //                   "Argument " + i + " (" + formalArguments.get(i) + ")" );
            String identifier = invocationArguments.expressions().get(i).toString();
            identifier = super.bindings.getOrDefault(identifier, identifier);
            bindings.put(formalArguments.get(i), identifier);
        }
        return bindings;
    }

    private void ensureIsIdentifier(ExpressionNode expression, String description) {
        if (expression instanceof NameNode) return;
        if (expression instanceof ReferenceNode) {
            ReferenceNode referenceExpression = (ReferenceNode)expression;
            if (referenceExpression.getArguments().isEmpty() && referenceExpression.getOutput() == null)
                return;
        }
        throw new IllegalArgumentException(description + " must be an identifier but is '" + expression + "'");
    }

    public Map<ReferenceNode.Reference, TensorType> featureTypes() {
        return Collections.unmodifiableMap(featureTypes);
    }

    @Override
    public MapEvaluationTypeContext withBindings(Map<String, String> bindings) {
        if (bindings.isEmpty() && this.bindings.isEmpty()) return this;
        return new MapEvaluationTypeContext(functions(), bindings, featureTypes);
    }

}
