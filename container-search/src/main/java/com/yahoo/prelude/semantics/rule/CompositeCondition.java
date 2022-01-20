// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A condition which contains a list of conditions
 *
 * @author bratseth
 */
public abstract class CompositeCondition extends Condition {

    private final List<Condition> conditions = new java.util.ArrayList<>();

    public CompositeCondition() {
    }

    public void preMatchHook(RuleEvaluation e) {
        super.preMatchHook(e);
        if (e.getTraceLevel() >= 3) {
            e.trace(3, "Evaluating '" + this + "'"  + " at " + e.currentItem());
            e.indentTrace();
        }
    }

    public void postMatchHook(RuleEvaluation e) {
        if (e.getTraceLevel() >= 3) {
            e.unindentTrace();
        }
    }

    protected boolean hasOpenChoicepoint(RuleEvaluation evaluation) {
        for (Iterator<Condition> i = conditionIterator(); i.hasNext(); ) {
            Condition subCondition = i.next();
            if (subCondition.hasOpenChoicepoint(evaluation))
                return true;
        }
        return false;
    }

    public void addCondition(Condition condition) {
        conditions.add(condition);
        condition.setParent(this);
    }

    /** Sets the condition at the given index */
    public void setCondition(int index, Condition condition) {
        conditions.set(index, condition);
    }

    /** Returns the number of subconditions */
    public int conditionSize() { return conditions.size(); }

    /**
     * Returns the condition at the given index
     *
     * @param i the 0-base index
     * @return the condition at this index
     * @throws IndexOutOfBoundsException if there is no condition at this index
     */
    public Condition getCondition(int i) {
        return conditions.get(i);
    }

    /**
     * Returns the condition at the given index
     *
     * @param i the 0-base index
     * @return the removed condition
     * @throws IndexOutOfBoundsException if there is no condition at this index
     */
    public Condition removeCondition(int i) {
        Condition condition = conditions.remove(i);
        condition.setParent(null);
        return condition;
    }

    /** Returns an iterator of the immediate children of this condition */
    public Iterator<Condition> conditionIterator() { return conditions.iterator(); }

    public List<Condition> conditions() { return Collections.unmodifiableList(conditions); }

    public void makeReferences(RuleBase rules) {
        for (Iterator<Condition> i = conditionIterator(); i.hasNext(); ) {
            Condition condition = i.next();
            condition.makeReferences(rules);
        }
    }

    /** Whether this should be output with parentheses, default is parent!=null */
    protected boolean useParentheses() {
        return getParent() != null;
    }

    protected String toInnerString(String conditionSeparator) {
        if (getLabel() != null)
            return getLabel() + ":(" + conditionsToString(conditionSeparator) + ")";
        else if (useParentheses())
            return "(" + conditionsToString(conditionSeparator) + ")";
        else
            return conditionsToString(conditionSeparator);
    }

    protected final String conditionsToString(String conditionSeparator) {
        StringBuilder buffer = new StringBuilder();
        for (Iterator<Condition> i = conditionIterator(); i.hasNext(); ) {
            buffer.append(i.next().toString());
            if (i.hasNext())
                buffer.append(conditionSeparator);
        }
        return buffer.toString();
    }

    /** Returns whether all the conditions of this matches the current evaluation state */
    protected final boolean allSubConditionsMatches(RuleEvaluation e) {
        for (Iterator<Condition> i = conditionIterator(); i.hasNext(); ) {
            Condition subCondition = i.next();
            if ( ! subCondition.matches(e))
                return false;
        }
        return true;
    }

}
