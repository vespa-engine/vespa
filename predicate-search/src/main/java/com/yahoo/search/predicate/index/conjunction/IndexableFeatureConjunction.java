// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index.conjunction;

import com.yahoo.document.predicate.FeatureConjunction;
import com.yahoo.document.predicate.FeatureSet;
import com.yahoo.document.predicate.Negation;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.search.predicate.index.Feature;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * IndexableFeatureConjunction is a post-processed {@link FeatureConjunction} which can be indexed by {@link ConjunctionIndex}.
 *
 * @author bjorncs
 */
public class IndexableFeatureConjunction {

    /** Conjunction id */
    public final long id;
    /** K value - number of non-negated operands */
    public final int k;
    // Hashed features from non-negated feature sets.
    public final Set<Long> features = new HashSet<>();
    // Hash features from negated feature sets.
    public final Set<Long> negatedFeatures = new HashSet<>();

    public IndexableFeatureConjunction(FeatureConjunction conjunction) {
        List<Predicate> operands = conjunction.getOperands();
        int nNegatedFeatureSets = 0;
        for (Predicate operand : operands) {
            if (operand instanceof FeatureSet) {
                addFeatures((FeatureSet)operand, features);
            } else {
                FeatureSet featureSet = (FeatureSet)((Negation) operand).getOperand();
                addFeatures(featureSet, negatedFeatures);
                ++nNegatedFeatureSets;
            }
        }

        id = calculateConjunctionId();
        k = operands.size() - nNegatedFeatureSets;
    }

    private static void addFeatures(FeatureSet featureSet, Set<Long> features) {
        String key = featureSet.getKey();
        featureSet.getValues().forEach(value -> features.add(Feature.createHash(key, value)));
    }

    private long calculateConjunctionId() {
        long posHash = 0;
        for (long feature : features) {
            posHash ^= feature;
        }
        long negHash = 0;
        for (long feature : negatedFeatures) {
            negHash ^= feature;
        }
        return (posHash + 3 * negHash) | 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndexableFeatureConjunction that = (IndexableFeatureConjunction) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

}
