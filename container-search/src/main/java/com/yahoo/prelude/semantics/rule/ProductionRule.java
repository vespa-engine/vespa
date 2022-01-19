// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A query rewriting rule.
 *
 * @author bratseth
 */
public abstract class ProductionRule {

    /** What must be true for this rule to be true */
    private Condition condition;

    /** What is produced when this rule is true */
    private ProductionList production = new ProductionList();

    /** The set of match name Strings which the production part of this rule references */
    private final Set<String> matchReferences = new java.util.LinkedHashSet<>();

    /** Sets what must be true for this rule to be true */
    public void setCondition(Condition condition) { this.condition = condition; }

    public Condition getCondition() { return condition; }

    /** Sets what is produced when this rule is true */
    public void setProduction(ProductionList production) { this.production = production; }

    public ProductionList getProduction() { return production; }

    /** Returns whether this rule matches the given query */
    public boolean matches(RuleEvaluation e) {
        e.setMatchReferences(matchReferences);
        return condition.matches(e);
    }

    /**
     * Returns the set of context names the production of this rule references
     *
     * @return an unmodifiable Set of condition context name Strings
     */
    public Set<String> matchReferences() {
        return Collections.unmodifiableSet(matchReferences);
    }

    public void makeReferences(RuleBase rules) {
        condition.makeReferences(rules);
        production.addMatchReferences(matchReferences);
    }

    /** Carries out the production of this rule */
    public void produce(RuleEvaluation e) {
        production.produce(e);
    }

    /**
     * Returns the canonical string representation of this rule.
     * This string representation can always be reparsed to produce an
     * identical rule to this one.
     */
    @Override
    public String toString() {
        return condition.toString() + " " + getSymbol() + " " + production.toString();
    }

    /**
     * Returns the symbol of this production rule.
     * All rules are on the form <code>condition symbol production</code>.
     */
    protected abstract String getSymbol();

    /**
     * Returns true if it is known that this rule matches its own output.
     * If it does, it will only be evaluated once, to avoid infinite loops.
     * This default implementation returns false;
     */
    public boolean isLoop() {
        // TODO: There are many more possible loops, we should probably detect a few more obvious ones
        if (conditionIsEllipsAndOtherNameSpacesOnly(getCondition())) return true;
        if (producesItself()) return true;
        return false;
    }

    private boolean conditionIsEllipsAndOtherNameSpacesOnly(Condition condition) {
        if (condition instanceof EllipsisCondition) return true;
        if (! (condition instanceof CompositeCondition)) return false;
        for (Iterator<Condition> i = ((CompositeCondition)condition).conditionIterator(); i.hasNext(); ) {
            Condition child = i.next();
            if (child.getNameSpace() == null && conditionIsEllipsAndOtherNameSpacesOnly(child))
                return true;
        }
        return false;
    }

    private boolean producesItself() {
        return production.productionList()
                         .stream()
                         .anyMatch(p -> (p instanceof ReferenceTermProduction) && ((ReferenceTermProduction)p).producesAll());
    }

}
