// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

    static record GlobalPhaseData(Supplier<FunctionEvaluator> functionEvaluatorSource,
                                  Collection<String> matchFeaturesToHide,
                                  int rerankCount,
                                  List<String> needInputs) {}

    private Map<String, GlobalPhaseData> profilesWithGlobalPhase = new HashMap<>();

    Optional<GlobalPhaseData> getGlobalPhaseData(String rankProfile) {
        return Optional.ofNullable(profilesWithGlobalPhase.get(rankProfile));
    }

    private void extractGlobalPhaseData(RankProfilesConfig rankProfilesConfig) {
        for (var rp : rankProfilesConfig.rankprofile()) {
            String name = rp.name();
            Supplier<FunctionEvaluator> functionEvaluatorSource = null;
            int rerankCount = -1;
            List<String> needInputs = null;
            Set<String> namesToHide = new HashSet<>();
            for (var prop : rp.fef().property()) {
                if (prop.name().equals("vespa.globalphase.rerankcount")) {
                    rerankCount = Integer.valueOf(prop.value());
                }
                if (prop.name().equals("vespa.rank.globalphase")) {
                    var model = modelForRankProfile(name);
                    functionEvaluatorSource = () -> model.evaluatorOf("globalphase");
                    var evaluator = functionEvaluatorSource.get();
                    needInputs = List.copyOf(evaluator.function().arguments());
                }
                if (prop.name().equals("vespa.hidden.matchfeature")) {
                    namesToHide.add(prop.value());
                }
            }
            if (functionEvaluatorSource != null && needInputs != null) {
                profilesWithGlobalPhase.put(name, new GlobalPhaseData(functionEvaluatorSource, namesToHide, rerankCount, needInputs));
            }
        }
    }

}
