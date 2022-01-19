// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import java.util.Set;

import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A new term produced by a production rule
 *
 * @author bratseth
 */
public abstract class Production {

    /** True to add, false to replace, default true */
    protected boolean replacing = true;

    /** The (0-base) position of this term in the productions of this rule */
    private int position = 0;

    /** The weight (strength) of this production as a percentage (default is 100) */
    private int weight = 100;

    /** Creates a produced template term with no label and the default type */
    public Production() {
    }

    /** True to replace, false to add, if this production can do both. Default true. */
    public void setReplacing(boolean replacing) { this.replacing = replacing; }

    public int getPosition() { return position; }

    public void setPosition(int position) { this.position = position; }

    /** Sets the weight of this production as a percentage (default is 100) */
    public void setWeight(int weight) { this.weight = weight; }

    /** Returns the weight of this production as a percentage (default is 100) */
    public int getWeight() { return weight; }

    /**
     * Produces this at the current match
     *
     * @param e the evaluation context containing the current match and the query
     * @param offset the offset position at which to produce this. Offsets are used to produce multiple items
     *        at one position, inserted in the right order.
     */
    public abstract void produce(RuleEvaluation e, int offset);

    /**
     * Called to add the references into the condition of this rule made by this production
     * into the given set. The default implementation is void, override for productions
     * which refers to the condition
     */
    void addMatchReferences(Set<String> matchReferences) { }

    /** All instances of this produces a parseable string output */
    @Override
    public final String toString() {
        return toInnerString() + (getWeight()!=100 ? ("!" + getWeight()) : "");
    }

    /** All instances of this produces a parseable string output */
    protected abstract String toInnerString();

}
