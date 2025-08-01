// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import com.yahoo.tensor.Tensor;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

class HitRescorer {

    private static final Logger logger = Logger.getLogger(HitRescorer.class.getName());

    private final Supplier<Evaluator> mainEvalSrc;
    private final List<MatchFeatureInput> mainFromMF;
    private final List<NormalizerContext> normalizers;

    public HitRescorer(Supplier<Evaluator> mainEvalSrc,
                       List<MatchFeatureInput> mainFromMF,
                       List<NormalizerContext> normalizers) {
        this.mainEvalSrc = mainEvalSrc;
        this.mainFromMF = mainFromMF;
        this.normalizers = normalizers;
    }

    void preprocess(WrappedHit wrapped) {
        for (var n : normalizers) {
            var scorer = n.evalSource().get();
            double val = evalScorer(wrapped, scorer, n.fromMF());
            wrapped.setIdx(n.normalizer().addInput(val));
        }
    }

    void runNormalizers() {
        for (var n : normalizers) {
            n.normalizer().normalize();
        }
    }

    double rescoreHit(WrappedHit wrapped) {
        var scorer = mainEvalSrc.get();
        for (var n : normalizers) {
            double normalizedValue = n.normalizer().getOutput(wrapped.getIdx());
            scorer.bind(n.name(), Tensor.from(normalizedValue));
        }
        double newScore = evalScorer(wrapped, scorer, mainFromMF);
        wrapped.setScore(newScore);
        return newScore;
    }

    public static final String RELEVANCE_SCORE = "relevanceScore";

    private static double evalScorer(WrappedHit wrapped, Evaluator scorer, List<MatchFeatureInput> fromMF) {
        for (var argSpec : fromMF) {
            String name = argSpec.inputName();
            var asTensor = wrapped.getTensor(argSpec.matchFeatureName());
            if (asTensor != null) {
                scorer.bind(name, asTensor);
            } else if (name.equals(RELEVANCE_SCORE)) {
                scorer.bind(name, Tensor.from(wrapped.getScore()));
            } else {
                logger.warning("Missing match-feature for Evaluator argument: " + name);
                return 0.0;
            }
        }
        return scorer.evaluateScore();
    }
}
