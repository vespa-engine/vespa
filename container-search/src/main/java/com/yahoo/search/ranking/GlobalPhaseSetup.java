// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import ai.vespa.models.evaluation.FunctionEvaluator;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.RankProfilesConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

class GlobalPhaseSetup {

    final FunEvalSpec globalPhaseEvalSpec;
    final int rerankCount;
    final double rankScoreDropLimit;
    final Collection<String> matchFeaturesToHide;
    final List<NormalizerSetup> normalizers;
    final Map<String, Tensor> defaultValues;

    GlobalPhaseSetup(FunEvalSpec globalPhaseEvalSpec,
                     final int rerankCount,
                     final double rankScoreDropLimit,
                     Collection<String> matchFeaturesToHide,
                     List<NormalizerSetup> normalizers,
                     Map<String, Tensor> defaultValues)
    {
        this.globalPhaseEvalSpec = globalPhaseEvalSpec;
        this.rerankCount = rerankCount;
        this.rankScoreDropLimit = rankScoreDropLimit;
        this.matchFeaturesToHide = matchFeaturesToHide;
        this.normalizers = normalizers;
        this.defaultValues = defaultValues;
    }

    static class DefaultQueryFeatureExtractor {
        final String baseName;
        final String qfName;
        TensorType type = null;
        Tensor value = null;
        DefaultQueryFeatureExtractor(String unwrappedQueryFeature) {
            baseName = unwrappedQueryFeature;
            qfName = "query(" + baseName + ")";
        }
        List<String> lookingFor() {
            return List.of(qfName, "vespa.type.query." + baseName);
        }
        void accept(String key, String propValue) {
            if (key.equals(qfName)) {
                this.value = Tensor.from(propValue);
            } else {
                this.type = TensorType.fromSpec(propValue);
            }
        }
        Tensor extract() {
            if (value != null) {
                return value;
            }
            if (type != null) {
                return Tensor.Builder.of(type).build();
            }
            return Tensor.from(0.0);
        }
    }

    static private Map<String, Tensor> extraDefaultQueryFeatureValues(RankProfilesConfig.Rankprofile rp,
                                                                      List<String> fromQuery,
                                                                      List<NormalizerSetup> normalizers)
    {
        Map<String, DefaultQueryFeatureExtractor> extractors = new HashMap<>();
        for (String fn : fromQuery) {
            extractors.put(fn, new DefaultQueryFeatureExtractor(fn));
        }
        for (var n : normalizers) {
            for (String fn : n.inputEvalSpec().fromQuery()) {
                extractors.put(fn, new DefaultQueryFeatureExtractor(fn));
            }
        }
        Map<String, DefaultQueryFeatureExtractor> targets = new HashMap<>();
        for (var extractor : extractors.values()) {
            for (String key : extractor.lookingFor()) {
                var old = targets.put(key, extractor);
                if (old != null) {
                    throw new IllegalStateException("Multiple targets for key: " + key);
                }
            }
        }
        for (var prop : rp.fef().property()) {
            var extractor = targets.get(prop.name());
            if (extractor != null) {
                extractor.accept(prop.name(), prop.value());
            }
        }
        Map<String, Tensor> defaultValues = new HashMap<>();
        for (var extractor : extractors.values()) {
            defaultValues.put(extractor.qfName, extractor.extract());
        }
        return defaultValues;
    }

    static class InputResolver {
        final List<String> usedNormalizers = new ArrayList<>();
        final List<String> fromQuery = new ArrayList<>();
        final List<MatchFeatureInput> fromMF = new ArrayList<>();
        private final Set<String> availableMatchFeatures;
        private final Map<String, String> renamedFeatures;
        private final Set<String> availableNormalizers;

        InputResolver(Set<String> availableMatchFeatures,
                      Map<String, String> renamedFeatures,
                      Set<String> availableNormalizers)
        {
            this.availableMatchFeatures = availableMatchFeatures;
            this.renamedFeatures = renamedFeatures;
            this.availableNormalizers = availableNormalizers;
        }
        void resolve(Collection<String> allInputs) {
            for (var input : allInputs) {
                String queryFeatureName = asQueryFeature(input);
                if (queryFeatureName != null) {
                    fromQuery.add(queryFeatureName);
                } else if (availableNormalizers.contains(input)) {
                    usedNormalizers.add(input);
                } else if (availableMatchFeatures.contains(input) ||
                            // per-hit pseudo match feature:
                           input.equals(HitRescorer.RELEVANCE_SCORE))
                {
                    String mfName = renamedFeatures.getOrDefault(input, input);
                    fromMF.add(new MatchFeatureInput(input, mfName));
                } else if (renamedFeatures.containsValue(input)) {
                    fromMF.add(new MatchFeatureInput(input, input));
                } else {
                    throw new IllegalArgumentException("Bad config, missing global-phase input: " + input);
                }
            }
        }
    }

    static GlobalPhaseSetup maybeMakeSetup(RankProfilesConfig.Rankprofile rp, RankProfilesEvaluator modelEvaluator) {
        var model = modelEvaluator.modelForRankProfile(rp.name());
        Map<String, RankProfilesConfig.Rankprofile.Normalizer> availableNormalizers = new HashMap<>();
        for (var n : rp.normalizer()) {
            availableNormalizers.put(n.name(), n);
        }
        Supplier<FunctionEvaluator> functionEvaluatorSource = null;
        int rerankCount = -1;
        double rankScoreDropLimit = -Double.MAX_VALUE;
        Set<String> namesToHide = new HashSet<>();
        Set<String> matchFeatures = new HashSet<>();
        Map<String, String> renameFeatures = new HashMap<>();
        String renameFrom = null;
        for (var prop : rp.fef().property()) {
            if (prop.name().equals("vespa.globalphase.rerankcount")) {
                rerankCount = Integer.parseInt(prop.value());
            }
            if (prop.name().equals("vespa.globalphase.rankscoredroplimit")) {
                rankScoreDropLimit = Double.parseDouble(prop.value());
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
                if (renameFrom == null) {
                    renameFrom = prop.value();
                } else {
                    renameFeatures.put(renameFrom, prop.value());
                    renameFrom = null;
                }
            }
        }
        // ignore renamed summary features (not available as match features)
        renameFeatures.entrySet().removeIf(entry -> !matchFeatures.contains(entry.getKey()));
        if (rerankCount < 0) {
            rerankCount = 100;
        }
        if (functionEvaluatorSource != null) {
            var mainResolver = new InputResolver(matchFeatures, renameFeatures, availableNormalizers.keySet());
            var evaluator = functionEvaluatorSource.get();
            var allInputs = List.copyOf(evaluator.function().arguments());
            mainResolver.resolve(allInputs);
            List<NormalizerSetup> normalizers = new ArrayList<>();
            for (var input : mainResolver.usedNormalizers) {
                var cfg = availableNormalizers.get(input);
                String normInput = cfg.input();
                if (matchFeatures.contains(normInput) || renameFeatures.containsValue(normInput)) {
                    Supplier<Evaluator> normSource = () -> new DummyEvaluator(normInput);
                    normalizers.add(makeNormalizerSetup(cfg, matchFeatures, renameFeatures, normSource, List.of(normInput), rerankCount));
                } else {
                    Supplier<FunctionEvaluator> normSource = () -> model.evaluatorOf(normInput);
                    var normInputs = List.copyOf(normSource.get().function().arguments());
                    var normSupplier = SimpleEvaluator.wrap(normSource);
                    normalizers.add(makeNormalizerSetup(cfg, matchFeatures, renameFeatures, normSupplier, normInputs, rerankCount));
                }
            }
            Supplier<Evaluator> supplier = SimpleEvaluator.wrap(functionEvaluatorSource);
            var gfun = new FunEvalSpec(supplier, mainResolver.fromQuery, mainResolver.fromMF);
            var defaultValues = extraDefaultQueryFeatureValues(rp, mainResolver.fromQuery, normalizers);
            return new GlobalPhaseSetup(gfun, rerankCount, rankScoreDropLimit, namesToHide, normalizers, defaultValues);
        }
        return null;
    }

    private static NormalizerSetup makeNormalizerSetup(RankProfilesConfig.Rankprofile.Normalizer cfg,
                                                       Set<String> matchFeatures,
                                                       Map<String, String> renamedFeatures,
                                                       Supplier<Evaluator> evalSupplier,
                                                       List<String> normInputs,
                                                       int rerankCount)
    {
        var normResolver = new InputResolver(matchFeatures, renamedFeatures, Set.of());
        normResolver.resolve(normInputs);
        var fun = new FunEvalSpec(evalSupplier, normResolver.fromQuery, normResolver.fromMF);
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
