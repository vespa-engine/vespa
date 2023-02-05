// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.MapEvaluationTypeContext;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Resolves and assigns types to all functions in a ranking expression, and
 * validates the types of all ranking expressions under a schema instance:
 * Some operators constrain the types of inputs, and first-and second-phase expressions
 * must return scalar values.
 *
 * In addition, the existence of all referred attribute, query and constant
 * features is ensured.
 *
 * @author bratseth
 */
public class RankingExpressionTypeResolver extends Processor {

    private final QueryProfileRegistry queryProfiles;

    public RankingExpressionTypeResolver(Schema schema,
                                         DeployLogger deployLogger,
                                         RankProfileRegistry rankProfileRegistry,
                                         QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
        this.queryProfiles = queryProfiles.getRegistry();
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (documentsOnly) return;

        Set<Reference> warnedAbout = new HashSet<>();
        for (RankProfile profile : rankProfileRegistry.rankProfilesOf(schema)) {
            try {
                resolveTypesIn(profile, validate, warnedAbout);
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("In " + (schema != null ? schema + ", " : "") + profile, e);
            }
        }
    }

    /**
     * Resolves the types of all functions in the given profile
     *
     * @throws IllegalArgumentException if validate is true and the given rank profile does not produce valid types
     */
    private void resolveTypesIn(RankProfile profile, boolean validate, Set<Reference> warnedAbout) {
        MapEvaluationTypeContext context = profile.typeContext(queryProfiles);
        for (Map.Entry<String, RankProfile.RankingExpressionFunction> function : profile.getFunctions().entrySet()) {
            ExpressionFunction expressionFunction = function.getValue().function();
            if (hasUntypedArguments(expressionFunction)) continue;

            // Add any missing inputs for type resolution
            for (String argument : expressionFunction.arguments()) {
                Reference ref = Reference.fromIdentifier(argument);
                if (context.getType(ref).equals(TensorType.empty)) {
                    context.setType(ref, expressionFunction.getArgumentType(argument));
                }
            }
            context.forgetResolvedTypes();

            TensorType type = resolveType(expressionFunction.getBody(), "function '" + function.getKey() + "'", context);
            function.getValue().setReturnType(type);
        }

        if (validate) {
            profile.getSummaryFeatures().forEach(f -> resolveType(f, "summary feature " + f, context));
            ensureValidDouble(profile.getFirstPhaseRanking(), "first-phase expression", context);
            ensureValidDouble(profile.getSecondPhaseRanking(), "second-phase expression", context);
            ensureValidDouble(profile.getGlobalPhaseRanking(), "global-phase expression", context);
            if ( ( context.tensorsAreUsed() || profile.isStrict())
                 && ! context.queryFeaturesNotDeclared().isEmpty()
                 && ! warnedAbout.containsAll(context.queryFeaturesNotDeclared())) {
                if (profile.isStrict())
                    throw new IllegalArgumentException(profile + " is strict but is missing a " +
                                                       "declaration of inputs " + context.queryFeaturesNotDeclared());
                else
                    deployLogger.logApplicationPackage(Level.WARNING, "The following inputs used in " +
                                                                      profile + " are not declared " +
                                                                      "and will be interpreted as scalars, not tensors: " +
                                                                      context.queryFeaturesNotDeclared());
                warnedAbout.addAll(context.queryFeaturesNotDeclared());
            }
        }
    }

    private boolean hasUntypedArguments(ExpressionFunction function) {
        return function.arguments().size() > function.argumentTypes().size();
    }

    private TensorType resolveType(RankingExpression expression, String expressionDescription, TypeContext<Reference> context) {
        if (expression == null) return null;
        return resolveType(expression.getRoot(), expressionDescription, context);
    }

    private TensorType resolveType(ExpressionNode expression, String expressionDescription, TypeContext<Reference> context) {
        TensorType type;
        try {
            type = expression.type(context);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The " + expressionDescription + " is invalid", e);
        }
        if (type == null) // Not expected to happen
            throw new IllegalStateException("Could not determine the type produced by " + expressionDescription);
        return type;
    }

    private void ensureValidDouble(RankingExpression expression, String expressionDescription, TypeContext<Reference> context) {
        if (expression == null) return;
        TensorType type = resolveType(expression, expressionDescription, context);
        if ( ! type.equals(TensorType.empty))
            throw new IllegalArgumentException("The " + expressionDescription + " must produce a double " +
                                               "(a tensor with no dimensions), but produces " + type);
    }

}
