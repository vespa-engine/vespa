// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.optimization;

import com.yahoo.document.predicate.Conjunction;
import com.yahoo.document.predicate.Disjunction;
import com.yahoo.document.predicate.Negation;
import com.yahoo.document.predicate.Predicate;

import java.util.ArrayList;
import java.util.List;

/**
 * Reorders not nodes to improve the efficiency of the z-star posting list compression.
 * It puts negative children first in AND-nodes, and last in OR-nodes.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class NotNodeReorderer implements PredicateProcessor {

    /**
     * Returns true if the predicate ends in a negation.
     */
    public boolean processSubTree(Predicate predicate) {
        if (predicate == null) {
            return false;
        }
        if (predicate instanceof Negation) {
            // All negations are for leaf-nodes after AndOrSimplifier has run.
            return true;
        } else if (predicate instanceof Conjunction) {
            List<Predicate> in = ((Conjunction)predicate).getOperands();
            List<Predicate> out = new ArrayList<>(in.size());
            List<Predicate> positiveChildren = new ArrayList<>(in.size());
            for (Predicate operand : in) {
                if (processSubTree(operand)) {
                    out.add(operand);
                } else {
                    positiveChildren.add(operand);
                }
            }
            out.addAll(positiveChildren);
            ((Conjunction)predicate).setOperands(out);
            return positiveChildren.isEmpty();
        } else if (predicate instanceof Disjunction) {
            List<Predicate> in = ((Disjunction)predicate).getOperands();
            List<Predicate> out = new ArrayList<>(in.size());
            List<Predicate> negativeChildren = new ArrayList<>(in.size());
            for (Predicate operand : in) {
                if (processSubTree(operand)) {
                    negativeChildren.add(operand);
                } else {
                    out.add(operand);
                }
            }
            out.addAll(negativeChildren);
            ((Disjunction)predicate).setOperands(out);
            return !negativeChildren.isEmpty();
        }
        return false;
    }

    @Override
    public Predicate process(Predicate predicate, PredicateOptions options) {
        processSubTree(predicate);
        return predicate;
    }

}
