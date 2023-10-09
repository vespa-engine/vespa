// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public class Disjunction extends PredicateOperator {

    private List<Predicate> operands;

    public Disjunction(Predicate... operands) {
        this(Arrays.asList(operands));
    }

    public Disjunction(List<? extends Predicate> operands) {
        this.operands = new ArrayList<>(operands);
    }

    public Disjunction addOperand(Predicate operand) {
        operands.add(operand);
        return this;
    }

    public Disjunction addOperands(Collection<? extends Predicate> operands) {
        this.operands.addAll(operands);
        return this;
    }

    public Disjunction setOperands(Collection<? extends Predicate> operands) {
        this.operands.clear();
        this.operands.addAll(operands);
        return this;
    }

    @Override
    public List<Predicate> getOperands() {
        return operands;
    }

    @Override
    public Disjunction clone() throws CloneNotSupportedException {
        Disjunction obj = (Disjunction)super.clone();
        obj.operands = new ArrayList<>(operands.size());
        for (Predicate operand : operands) {
            obj.operands.add(operand.clone());
        }
        return obj;
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
        if (!(obj instanceof Disjunction)) {
            return false;
        }
        Disjunction rhs = (Disjunction)obj;
        if (!operands.equals(rhs.operands)) {
            return false;
        }
        return true;
    }

    @Override
    protected void appendTo(StringBuilder out) {
        for (Iterator<Predicate> it = operands.iterator(); it.hasNext(); ) {
            Predicate operand = it.next();
            if (operand instanceof Conjunction) {
                out.append('(');
                operand.appendTo(out);
                out.append(')');
            } else {
                operand.appendTo(out);
            }
            if (it.hasNext()) {
                out.append(" or ");
            }
        }
    }

}
