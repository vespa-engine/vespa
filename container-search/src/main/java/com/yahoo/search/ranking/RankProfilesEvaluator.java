// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.ranking;

import ai.vespa.models.evaluation.FunctionEvaluator;
import ai.vespa.models.evaluation.Model;
import ai.vespa.models.evaluation.ModelsEvaluator;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * proxy for model-evaluation components
 * @author arnej
 */
@Beta
public class RankProfilesEvaluator extends AbstractComponent {

    private final ModelsEvaluator evaluator;
    private static final Logger logger = Logger.getLogger(RankProfilesEvaluator.class.getName());

    @Inject
    public RankProfilesEvaluator(
            RankProfilesConfig rankProfilesConfig,
            RankingConstantsConfig constantsConfig,
            RankingExpressionsConfig expressionsConfig,
            OnnxModelsConfig onnxModelsConfig,
            FileAcquirer fileAcquirer)
    {
        this.evaluator = new ModelsEvaluator(
                rankProfilesConfig,
                constantsConfig,
                expressionsConfig,
                onnxModelsConfig,
                fileAcquirer);
        extractGlobalPhaseData(rankProfilesConfig);
    }

    public Model modelForRankProfile(String rankProfile) {
        var m = evaluator.models().get(rankProfile);
        if (m == null) {
            throw new IllegalArgumentException("unknown rankprofile: " + rankProfile);
        }
        return m;
    }

    public FunctionEvaluator evaluatorForFunction(String rankProfile, String functionName) {
        return modelForRankProfile(rankProfile).evaluatorOf(functionName);
    }

    private Map<String, GlobalPhaseSetup> profilesWithGlobalPhase = new HashMap<>();

    Optional<GlobalPhaseSetup> getGlobalPhaseSetup(String rankProfile) {
        return Optional.ofNullable(profilesWithGlobalPhase.get(rankProfile));
    }

    private void extractGlobalPhaseData(RankProfilesConfig rankProfilesConfig) {
        for (var rp : rankProfilesConfig.rankprofile()) {
            var setup = GlobalPhaseSetup.maybeMakeSetup(rp, this);
            if (setup != null) {
                profilesWithGlobalPhase.put(rp.name(), setup);
            }
        }
    }

}
