// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import java.util.List;
import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public class Negation extends PredicateOperator {

    private Predicate operand;

    public Negation(Predicate operand) {
        Objects.requireNonNull(operand, "operand");
        this.operand = operand;
    }

    public Negation setOperand(Predicate operand) {
        Objects.requireNonNull(operand, "operand");
        this.operand = operand;
        return this;
    }

    public Predicate getOperand() {
        return operand;
    }

    @Override
    public List<Predicate> getOperands() {
        return java.util.Arrays.asList(operand);
    }

    @Override
    public Negation clone() throws CloneNotSupportedException {
        Negation obj = (Negation)super.clone();
        obj.operand = operand.clone();
        return obj;
    }

    @Override
    public int hashCode() {
        return operand.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Negation)) {
            return false;
        }
        Negation rhs = (Negation)obj;
        if (!operand.equals(rhs.operand)) {
            return false;
        }
        return true;
    }

    @Override
    protected void appendTo(StringBuilder out) {
        if (operand instanceof FeatureSet) {
            ((FeatureSet)operand).appendNegatedTo(out);
        } else {
            out.append("not (");
            operand.appendTo(out);
            out.append(')');
        }
    }

}
