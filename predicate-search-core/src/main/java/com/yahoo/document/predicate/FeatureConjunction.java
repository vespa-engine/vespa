// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A FeatureConjunction is a special type of Conjunction where
 * all children are either a FeatureSet or a Negation (with an underlying FeatureSet).
 * The underlying FeatureSets may only have one value.
 *
 * @author bjorncs
 */
public class FeatureConjunction extends PredicateOperator {

    private final List<Predicate> operands;

    public FeatureConjunction(List<Predicate> operands) {
        validateOperands(operands);
        this.operands = new ArrayList<>(operands);
    }

    private static void validateOperands(List<Predicate> operands) {
        if (operands.size() <= 1) {
            throw new IllegalArgumentException("Number of operands must 2 or more, was: " + operands.size());
        }
        if (!operands.stream()
                .allMatch(FeatureConjunction::isValidFeatureConjunctionOperand)) {
            throw new IllegalArgumentException(
                    "A FeatureConjunction may only contain instances of Negation and FeatureSet, " +
                            "and a FeatureSet may only have one value.");
        }

        long uniqueKeys = operands.stream().map(FeatureConjunction::getFeatureSetKey).distinct().count();
        if (operands.size() > uniqueKeys) {
            throw new IllegalArgumentException("Each FeatureSet key must have a unique key.");
        }
    }

    private static String getFeatureSetKey(Predicate predicate) {
        if (predicate instanceof FeatureSet) {
            return ((FeatureSet) predicate).getKey();
        } else {
            Negation negation = (Negation) predicate;
            return ((FeatureSet) negation.getOperand()).getKey();
        }
    }

    public static boolean isValidFeatureConjunctionOperand(Predicate operand) {
        return operand instanceof Negation
                    && ((Negation) operand).getOperand() instanceof FeatureSet
                    && isValidFeatureConjunctionOperand(((Negation) operand).getOperand())
                || operand instanceof FeatureSet && ((FeatureSet) operand).getValues().size() == 1;
    }

    @Override
    public List<Predicate> getOperands() {
        return operands;
    }

    @Override
    protected void appendTo(StringBuilder out) {
        for (Iterator<Predicate> it = operands.iterator(); it.hasNext(); ) {
            Predicate operand = it.next();
            if (operand instanceof Disjunction) {
                out.append('(');
                operand.appendTo(out);
                out.append(')');
            } else {
                operand.appendTo(out);
            }
            if (it.hasNext()) {
                out.append(" conj ");
            }
        }
    }

    @Override
    public FeatureConjunction clone() throws CloneNotSupportedException {
        return new FeatureConjunction(operands);
    }

    @Override
    public int hashCode() {
        return operands.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof FeatureConjunction)) {
            return false;
        }
        FeatureConjunction rhs = (FeatureConjunction)obj;
        if (!operands.equals(rhs.operands)) {
            return false;
        }
        return true;
    }

}
