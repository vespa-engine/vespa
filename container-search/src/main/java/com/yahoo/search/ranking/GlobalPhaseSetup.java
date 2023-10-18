// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import ai.vespa.models.evaluation.FunctionEvaluator;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.RankProfilesConfig;

import java.util.*;
import java.util.function.Supplier;

class GlobalPhaseSetup {

    final FunEvalSpec globalPhaseEvalSpec;
    final int rerankCount;
    final Collection<String> matchFeaturesToHide;
    final List<NormalizerSetup> normalizers;
    final Map<String, Tensor> defaultValues;

    GlobalPhaseSetup(FunEvalSpec globalPhaseEvalSpec,
                     final int rerankCount,
                     Collection<String> matchFeaturesToHide,
                     List<NormalizerSetup> normalizers,
                     Map<String, Tensor> defaultValues)
    {
        this.globalPhaseEvalSpec = globalPhaseEvalSpec;
        this.rerankCount = rerankCount;
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
            var defaultValues = extraDefaultQueryFeatureValues(rp, fromQuery, normalizers);
            return new GlobalPhaseSetup(gfun, rerankCount, namesToHide, normalizers, defaultValues);
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
