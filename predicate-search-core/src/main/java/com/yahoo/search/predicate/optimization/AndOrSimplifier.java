// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.optimization;

import com.yahoo.document.predicate.Conjunction;
import com.yahoo.document.predicate.Disjunction;
import com.yahoo.document.predicate.Negation;
import com.yahoo.document.predicate.Predicate;

import java.util.ArrayList;
import java.util.List;

/**
 * Simplifies a predicate by:
 *  - Combining child-AND/OR nodes with direct parents of the same type
 *  - Pushing negations down to leaf nodes by using De Morgan's law.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class AndOrSimplifier implements PredicateProcessor {

    public Predicate simplifySubTree(Predicate predicate, boolean negated) {
        if (predicate == null) {
            return null;
        }
        if (predicate instanceof Negation) {
            return simplifySubTree(((Negation) predicate).getOperand(), !negated);
        } else if (predicate instanceof Conjunction) {
            List<Predicate> in = ((Conjunction)predicate).getOperands();
            List<Predicate> out = new ArrayList<>(in.size());
            for (Predicate operand : in) {
                operand = simplifySubTree(operand, negated);
                if (operand instanceof Conjunction) {
                    out.addAll(((Conjunction)operand).getOperands());
                } else {
                    out.add(operand);
                }
            }
            if (negated) {
                return new Disjunction(out);
            }
            ((Conjunction)predicate).setOperands(out);
        } else if (predicate instanceof Disjunction) {
            List<Predicate> in = ((Disjunction)predicate).getOperands();
            List<Predicate> out = new ArrayList<>(in.size());
            for (Predicate operand : in) {
                operand = simplifySubTree(operand, negated);
                if (operand instanceof Disjunction) {
                    out.addAll(((Disjunction)operand).getOperands());
                } else {
                    out.add(operand);
                }
            }
            if (negated) {
                return new Conjunction(out);
            }
            ((Disjunction)predicate).setOperands(out);
        } else {
            if (negated) {
                return new Negation(predicate);
            }
        }
        return predicate;
    }

    @Override
    public Predicate process(Predicate predicate, PredicateOptions options) {
        return simplifySubTree(predicate, false);
    }
}
