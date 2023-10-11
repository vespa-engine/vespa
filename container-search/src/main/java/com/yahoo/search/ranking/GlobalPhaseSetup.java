// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import ai.vespa.models.evaluation.FunctionEvaluator;

import com.yahoo.vespa.config.search.RankProfilesConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Supplier;

class GlobalPhaseSetup {

    final FunEvalSpec globalPhaseEvalSpec;
    final int rerankCount;
    final Collection<String> matchFeaturesToHide;
    final List<NormalizerSetup> normalizers;

    GlobalPhaseSetup(FunEvalSpec globalPhaseEvalSpec,
                     final int rerankCount,
                     Collection<String> matchFeaturesToHide,
                     List<NormalizerSetup> normalizers)
    {
        this.globalPhaseEvalSpec = globalPhaseEvalSpec;
        this.rerankCount = rerankCount;
        this.matchFeaturesToHide = matchFeaturesToHide;
        this.normalizers = normalizers;
    }

    static GlobalPhaseSetup maybeMakeSetup(RankProfilesConfig.Rankprofile rp, RankProfilesEvaluator modelEvaluator) {
        var model = modelEvaluator.modelForRankProfile(rp.name());
        Map<String, RankProfilesConfig.Rankprofile.Normalizer> availableNormalizers = new HashMap<>();
        for (var n : rp.normalizer()) {
            availableNormalizers.put(n.name(), n);
        }
        Supplier<FunctionEvaluator> functionEvaluatorSource = null;
        int rerankCount = -1;
        Set<String> namesToHide = new HashSet<>();
        Set<String> matchFeatures = new HashSet<>();
        Map<String, String> renameFeatures = new HashMap<>();
        String toRename = null;
        for (var prop : rp.fef().property()) {
            if (prop.name().equals("vespa.globalphase.rerankcount")) {
                rerankCount = Integer.valueOf(prop.value());
            }
            if (prop.name().equals("vespa.rank.globalphase")) {
                functionEvaluatorSource = () -> model.evaluatorOf("globalphase");
            }
            if (prop.name().equals("vespa.hidden.matchfeature")) {
                namesToHide.add(prop.value());
            }
            if (prop.name().equals("vespa.match.feature")) {
                matchFeatures.add(prop.value());
            }
            if (prop.name().equals("vespa.feature.rename")) {
                if (toRename == null) {
                    toRename = prop.value();
                } else {
                    renameFeatures.put(toRename, prop.value());
                    toRename = null;
                }
            }
        }
        for (var entry : renameFeatures.entrySet()) {
            String old = entry.getKey();
            if (matchFeatures.contains(old)) {
                matchFeatures.remove(old);
                matchFeatures.add(entry.getValue());
            }
        }
        if (rerankCount < 0) {
            rerankCount = 100;
        }
        if (functionEvaluatorSource != null) {
            var evaluator = functionEvaluatorSource.get();
            var allInputs = List.copyOf(evaluator.function().arguments());
            List<String> fromMF = new ArrayList<>();
            List<String> fromQuery = new ArrayList<>();
            List<NormalizerSetup> normalizers = new ArrayList<>();
            for (var input : allInputs) {
                String queryFeatureName = asQueryFeature(input);
                if (queryFeatureName != null) {
                    fromQuery.add(queryFeatureName);
                } else if (availableNormalizers.containsKey(input)) {
                    var cfg = availableNormalizers.get(input);
                    String normInput = cfg.input();
                    if (matchFeatures.contains(normInput)) {
                        Supplier<Evaluator> normSource = () -> new DummyEvaluator(normInput);
                        normalizers.add(makeNormalizerSetup(cfg, matchFeatures, normSource, List.of(normInput), rerankCount));
                    } else {
                        Supplier<FunctionEvaluator> normSource = () -> model.evaluatorOf(normInput);
                        var normInputs = List.copyOf(normSource.get().function().arguments());
                        var normSupplier = SimpleEvaluator.wrap(normSource);
                        normalizers.add(makeNormalizerSetup(cfg, matchFeatures, normSupplier, normInputs, rerankCount));
                    }
                } else if (matchFeatures.contains(input)) {
                    fromMF.add(input);
                } else {
                    throw new IllegalArgumentException("Bad config, missing global-phase input: " + input);
                }
            }
            Supplier<Evaluator> supplier = SimpleEvaluator.wrap(functionEvaluatorSource);
            var gfun = new FunEvalSpec(supplier, fromQuery, fromMF);
            return new GlobalPhaseSetup(gfun, rerankCount, namesToHide, normalizers);
        }
        return null;
    }

    private static NormalizerSetup makeNormalizerSetup(RankProfilesConfig.Rankprofile.Normalizer cfg,
                                                       Set<String> matchFeatures,
                                                       Supplier<Evaluator> evalSupplier,
                                                       List<String> normInputs,
                                                       int rerankCount)
    {
        List<String> fromQuery = new ArrayList<>();
        List<String> fromMF = new ArrayList<>();
        for (var input : normInputs) {
            String queryFeatureName = asQueryFeature(input);
            if (queryFeatureName != null) {
                fromQuery.add(queryFeatureName);
            } else if (matchFeatures.contains(input)) {
                fromMF.add(input);
            } else {
                throw new IllegalArgumentException("Bad config, missing normalizer input: " + input);
            }
        }
        var fun = new FunEvalSpec(evalSupplier, fromQuery, fromMF);
        return new NormalizerSetup(cfg.name(), makeNormalizerSupplier(cfg, rerankCount), fun);
    }

    private static Supplier<Normalizer> makeNormalizerSupplier(RankProfilesConfig.Rankprofile.Normalizer cfg, int rerankCount) {
        return switch (cfg.algo()) {
            case LINEAR -> () -> new LinearNormalizer(rerankCount);
            case RRANK -> () -> new ReciprocalRankNormalizer(rerankCount, cfg.kparam());
        };
    }

    static String asQueryFeature(String input) {
        var optRef = com.yahoo.searchlib.rankingexpression.Reference.simple(input);
        if (optRef.isPresent()) {
            var ref = optRef.get();
            if (ref.isSimple() && ref.name().equals("query")) {
                return ref.simpleArgument().get();
            }
        }
        return null;
    }
}
