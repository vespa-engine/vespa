// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.optimization;

import com.yahoo.document.predicate.Conjunction;
import com.yahoo.document.predicate.Disjunction;
import com.yahoo.document.predicate.FeatureSet;
import com.yahoo.document.predicate.Negation;
import com.yahoo.document.predicate.Predicate;

import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toList;

/**
 * Simplifies Disjunction nodes where all children are of type FeatureSet. All FeatureSet that share the same key
 * are merged into a single FeatureSet. The Disjunction is removed if all children merges into a single feature set.
 *
 * @author bjorncs
 */
public class OrSimplifier implements PredicateProcessor {

    @Override
    public Predicate process(Predicate predicate, PredicateOptions options) {
        return simplifyTree(predicate);
    }

    public Predicate simplifyTree(Predicate predicate) {
        if (predicate instanceof Disjunction) {
            Disjunction disjunction = (Disjunction) predicate;
            List<Predicate> newChildren =
                    disjunction.getOperands().stream().map(this::simplifyTree).collect(toList());
            return compressFeatureSets(newChildren);
        } else if (predicate instanceof Negation) {
            Negation negation = (Negation) predicate;
            negation.setOperand(simplifyTree(negation.getOperand()));
            return negation;
        } else if (predicate instanceof Conjunction) {
            Conjunction conjunction = (Conjunction) predicate;
            List<Predicate> newChildren =
                    conjunction.getOperands().stream().map(this::simplifyTree).collect(toList());
            conjunction.setOperands(newChildren);
            return conjunction;
        } else {
            return predicate;
        }
    }

    // Groups and merges the feature sets based on key.
    private static Predicate compressFeatureSets(List<Predicate> children) {
        List<Predicate> newChildren = children.stream().filter(p -> !(p instanceof FeatureSet)).collect(toList());
        children.stream()
                .filter(FeatureSet.class::isInstance)
                .map(FeatureSet.class::cast)
                .collect(groupingBy(FeatureSet::getKey,
                        reducing((f1, f2) -> {
                            f1.addValues(f2.getValues());
                            return f1;
                        })))
                .values()
                .stream()
                .map(Optional::get)
                .forEach(newChildren::add);
        if (newChildren.size() == 1) {
            return newChildren.get(0);
        } else {
            return new Disjunction(newChildren);
        }
    }

}
