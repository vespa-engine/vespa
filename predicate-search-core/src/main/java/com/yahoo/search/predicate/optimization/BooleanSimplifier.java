// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.optimization;

import com.yahoo.document.predicate.BooleanPredicate;
import com.yahoo.document.predicate.Conjunction;
import com.yahoo.document.predicate.Disjunction;
import com.yahoo.document.predicate.Negation;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.document.predicate.PredicateOperator;

import java.util.ArrayList;
import java.util.List;

/**
 * Simplifies a predicate by collapsing TRUE/FALSE-children into their parents.
 * E.g. if an AND node has a FALSE child, the entire node is replaced by FALSE.
 * Replaces single-child AND/OR-nodes with the child.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class BooleanSimplifier implements PredicateProcessor {

    public Predicate simplifySubTree(Predicate predicate) {
        if (predicate == null) {
            return null;
        }
        if (predicate instanceof Conjunction) {
            List<Predicate> in = ((PredicateOperator)predicate).getOperands();
            List<Predicate> out = new ArrayList<>(in.size());
            for (Predicate operand : in) {
                operand = simplifySubTree(operand);
                if (isFalse(operand)) {
                    return new BooleanPredicate(false);
                } else if (!isTrue(operand)) {
                    out.add(operand);
                }
            }
            if (out.size() == 1) {
                return out.get(0);
            } else if (out.size() == 0) {
                return new BooleanPredicate(true);
            }
            ((Conjunction)predicate).setOperands(out);
        } else if (predicate instanceof Disjunction) {
            List<Predicate> in = ((PredicateOperator)predicate).getOperands();
            List<Predicate> out = new ArrayList<>(in.size());
            for (Predicate operand : in) {
                operand = simplifySubTree(operand);
                if (isTrue(operand)) {
                    return new BooleanPredicate(true);
                } else if (!isFalse(operand)) {
                    out.add(operand);
                }
            }
            if (out.size() == 1) {
                return out.get(0);
            } else if (out.size() == 0) {
                return new BooleanPredicate(false);
            }
            ((Disjunction)predicate).setOperands(out);
        } else if (predicate instanceof Negation) {
            Predicate operand = ((Negation)predicate).getOperand();
            operand = simplifySubTree(operand);
            if (isTrue(operand)) {
                return new BooleanPredicate(false);
            } else if (isFalse(operand)) {
                return new BooleanPredicate(true);
            }
            ((Negation) predicate).setOperand(operand);
        }
        return predicate;
    }

    private boolean isFalse(Predicate predicate) {
        if (predicate instanceof BooleanPredicate) {
            return !((BooleanPredicate)predicate).getValue();
        }
        return false;
    }

    private boolean isTrue(Predicate predicate) {
        if (predicate instanceof BooleanPredicate) {
            return ((BooleanPredicate)predicate).getValue();
        }
        return false;
    }

    @Override
    public Predicate process(Predicate predicate, PredicateOptions options) {
        return simplifySubTree(predicate);
    }

}
