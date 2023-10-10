// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.optimization;

import com.yahoo.document.predicate.Conjunction;
import com.yahoo.document.predicate.Disjunction;
import com.yahoo.document.predicate.FeatureConjunction;
import com.yahoo.document.predicate.FeatureRange;
import com.yahoo.document.predicate.FeatureSet;
import com.yahoo.document.predicate.Negation;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.search.predicate.index.conjunction.ConjunctionIndex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Transforms Conjunctions with only (negated) {@link FeatureSet} instances to {@link FeatureConjunction}.
 * The {@link FeatureConjunction}s are indexed by the {@link ConjunctionIndex}.
 *
 * @author bjorncs
 */
public class FeatureConjunctionTransformer implements PredicateProcessor {

    // Only Conjunctions having less or equal number of FeatureSet operands than threshold are converted to FeatureConjunction.
    private static final int CONVERSION_THRESHOLD = Integer.MAX_VALUE;
    private final boolean useConjunctionAlgorithm;

    public FeatureConjunctionTransformer(boolean useConjunctionAlgorithm) {
        this.useConjunctionAlgorithm = useConjunctionAlgorithm;
    }

    @Override
    public Predicate process(Predicate predicate, PredicateOptions options) {
        if (useConjunctionAlgorithm) {
            return transform(predicate);
        }
        return predicate;
    }

    private static Predicate transform(Predicate predicate) {
        if (predicate instanceof Conjunction) {
            Conjunction conjunction = (Conjunction) predicate;
            conjunction.getOperands().replaceAll(FeatureConjunctionTransformer::transform);
            long nValidOperands = numberOfValidFeatureSetOperands(conjunction);
            if (nValidOperands > 1 && nValidOperands <= CONVERSION_THRESHOLD) {
                return convertConjunction(conjunction, nValidOperands);
            }
        } else if (predicate instanceof Disjunction) {
            ((Disjunction)predicate).getOperands().replaceAll(FeatureConjunctionTransformer::transform);
        } else if (predicate instanceof Negation) {
            Negation negation = (Negation) predicate;
            negation.setOperand(transform(negation.getOperand()));
        }
        return predicate;
    }

    /**
     * Conversion rules:
     *  1) A {@link FeatureConjunction} may only consist of FeatureSets having unique keys.
     *     If multiple {@link FeatureSet} share the same key, they have to be placed into separate FeatureConjunctions.
     *  2) A FeatureConjunction must have at least 2 operands.
     *  3) Any operand that is not a FeatureSet, negated or not,
     *     (e.g {@link FeatureRange}) cannot be placed into a FeatureConjunction.
     *  4) All FeatureSets may only have a single value.
     *
     *  See the tests in FeatureConjunctionTransformerTest for conversion examples.
     */
    private static Predicate convertConjunction(Conjunction conjunction, long nValidOperands) {
        List<Predicate> operands = conjunction.getOperands();
        // All operands are instance of FeatureSet are valid and may therefor be placed into a single FeatureConjunction.
        if (nValidOperands == operands.size()) {
            return new FeatureConjunction(operands);
        }

        List<Predicate> invalidFeatureConjunctionOperands = new ArrayList<>();
        List<Map<String, Predicate>> featureConjunctionOperandsList = new ArrayList<>();
        featureConjunctionOperandsList.add(new TreeMap<>());
        for (Predicate operand : operands) {
            if (FeatureConjunction.isValidFeatureConjunctionOperand(operand)) {
                addFeatureConjunctionOperand(featureConjunctionOperandsList, operand);
            } else {
                invalidFeatureConjunctionOperands.add(operand);
            }
        }

        // Create a Conjunction root.
        Conjunction newConjunction = new Conjunction();
        newConjunction.addOperands(invalidFeatureConjunctionOperands);
        // For all operand partitions: create FeatureConjunction if partition has more than a single predicate.
        for (Map<String, Predicate> featureConjunctionOperands : featureConjunctionOperandsList) {
            Collection<Predicate> values = featureConjunctionOperands.values();
            if (featureConjunctionOperands.size() == 1) {
                // Add single operand directly to root conjunction.
                newConjunction.addOperands(values);
            } else {
                newConjunction.addOperand(new FeatureConjunction(new ArrayList<>(values)));
            }
        }
        return newConjunction;
    }

    private static void addFeatureConjunctionOperand(List<Map<String, Predicate>> featureConjunctionOperandsList, Predicate operand) {
        String key = getFeatureSetKey(operand);
        for (Map<String, Predicate> featureConjunctionOperands : featureConjunctionOperandsList) {
            if (!featureConjunctionOperands.containsKey(key)) {
                featureConjunctionOperands.put(key, operand);
                return;
            }
        }
        Map<String, Predicate> conjunctionOperands = new TreeMap<>();
        conjunctionOperands.put(key, operand);
        featureConjunctionOperandsList.add(conjunctionOperands);
    }

    private static long numberOfValidFeatureSetOperands(Conjunction conjunction) {
        return conjunction.getOperands().stream()
                .filter(FeatureConjunction::isValidFeatureConjunctionOperand)
                .map(FeatureConjunctionTransformer::getFeatureSetKey)
                .distinct()
                .count();
    }

    private static String getFeatureSetKey(Predicate predicate) {
        if (predicate instanceof FeatureSet) {
            return ((FeatureSet) predicate).getKey();
        } else {
            Negation negation = (Negation) predicate;
            return ((FeatureSet) negation.getOperand()).getKey();
        }
    }

}
