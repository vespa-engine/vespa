// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.vespa.model.container.search.QueryProfiles;

public class RankingExpressionTypeValidator extends Processor {

    private final QueryProfileRegistry queryProfiles;

    public RankingExpressionTypeValidator(Search search,
                                          DeployLogger deployLogger,
                                          RankProfileRegistry rankProfileRegistry,
                                          QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
        this.queryProfiles = queryProfiles.getRegistry();
    }

    @Override
    public void process() {
        for (RankProfile profile : rankProfileRegistry.allRankProfiles()) {
            try {
                validate(profile);
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(search + ", " + profile + " is invalid", e);
            }
        }
    }

    /** Throws an IllegalArgumentException if the given rank profile does not produce valid type */
    private void validate(RankProfile profile) {
        TypeContext context = profile.typeContext(queryProfiles);
        for (RankProfile.Macro macro : profile.getMacros().values())
            if (macro.getRankingExpression() != null)
                macro.getRankingExpression().type(context); // type infer to throw on type conflicts
        ensureProducesDouble(profile.getFirstPhaseRanking(), "first-phase", context);
        ensureProducesDouble(profile.getSecondPhaseRanking(), "second-phase", context);
    }

    private void ensureProducesDouble(RankingExpression expression, String expressionDescription, TypeContext context) {
        if (expression == null) return;

        TensorType type = expression.type(context);
        if (type == null) // Not expected to happen
            throw new IllegalStateException("Could not determine the type produced by " + expressionDescription);
        if ( ! type.equals(TensorType.empty))
            throw new IllegalArgumentException(expressionDescription + " ranking expression must produce a double " +
                                               "but produces " + type);
    }

}
