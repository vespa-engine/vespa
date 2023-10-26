// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.yahoo.document.predicate.Predicate;
import com.yahoo.search.predicate.Config;
import com.yahoo.search.predicate.optimization.AndOrSimplifier;
import com.yahoo.search.predicate.optimization.BooleanSimplifier;
import com.yahoo.search.predicate.optimization.ComplexNodeTransformer;
import com.yahoo.search.predicate.optimization.FeatureConjunctionTransformer;
import com.yahoo.search.predicate.optimization.NotNodeReorderer;
import com.yahoo.search.predicate.optimization.OrSimplifier;
import com.yahoo.search.predicate.optimization.PredicateOptions;
import com.yahoo.search.predicate.optimization.PredicateProcessor;

/**
 * Prepares the predicate for indexing.
 * Performs several optimization passes on the predicate.
 *
 * @author bjorncs
 */
public class PredicateOptimizer {

    private final PredicateProcessor[] processors;
    private final PredicateOptions options;

    public PredicateOptimizer(Config config) {
        this.options = new PredicateOptions(config.arity, config.lowerBound, config.upperBound);
        processors = new PredicateProcessor[]{
                new AndOrSimplifier(),
                new BooleanSimplifier(),
                new ComplexNodeTransformer(),
                new OrSimplifier(),
                new NotNodeReorderer(),
                new FeatureConjunctionTransformer(config.useConjunctionAlgorithm)
        };
    }

    /**
     * @return The optimized predicate.
     */
    public Predicate optimizePredicate(Predicate predicate) {
        for (PredicateProcessor processor : processors) {
            predicate = processor.process(predicate, options);
        }
        return predicate;
    }

}
