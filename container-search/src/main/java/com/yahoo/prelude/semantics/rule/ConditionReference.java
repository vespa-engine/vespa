// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.querytransform.PhraseMatcher;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.RuleBaseException;
import com.yahoo.prelude.semantics.engine.Choicepoint;
import com.yahoo.prelude.semantics.engine.EvaluationException;
import com.yahoo.prelude.semantics.engine.FlattenedItem;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;
import com.yahoo.protect.Validator;

/**
 * A reference to a named condition
 *
 * @author bratseth
 */
public class ConditionReference extends Condition {

    /** The name of the referenced rule */
    private String conditionName;

    /**
     * The actual condition referenced by this, or null if not initialized or not found,
     * or if this is really an automata reference
     */
    private NamedCondition namedCondition;

    /**
     * True if this condition should be looked up in the automata
     * annotations of the item instead of by reference to another item
     */
    private boolean automataLookup = false;

    public ConditionReference(String conditionName) {
        this(null,conditionName);
    }

    public ConditionReference(String label,String conditionName) {
        super(label);
        Validator.ensureNotNull("Name of referenced condition",conditionName);
        this.conditionName = conditionName;
        setContextName(conditionName);
    }

    /** Returns the name of the referenced rule, never null */
    public String getConditionName() { return conditionName; }

    public void setConditionName(String name) { this.conditionName = name; }

    public boolean doesMatch(RuleEvaluation e) {
        if (automataLookup) return automataMatch(e);

        if (namedCondition == null)
            throw new EvaluationException("Condition reference '" + conditionName + "' not found or not initialized");

        return namedCondition.matches(e);
    }

    private boolean automataMatch(RuleEvaluation e) {
        FlattenedItem current = e.currentItem();
        if (current == null) return false;

        Object annotation = current.getItem().getAnnotation(conditionName);
        if (annotation == null) return false;
        if (! (annotation instanceof PhraseMatcher.Phrase)) return false;

        PhraseMatcher.Phrase phrase = (PhraseMatcher.Phrase)annotation;

        Choicepoint choicePoint = e.getChoicepoint(this,true);
        boolean matches = automataMatchPhrase(phrase,e);

        if (!matches && e.isInNegation()) { // TODO: Temporary hack! Works for single items only
            e.addMatch(current,null);
        }

        if ((!matches && !e.isInNegation() || (matches && e.isInNegation())))
            choicePoint.backtrackPosition();

        return matches;
    }

    private boolean automataMatchPhrase(PhraseMatcher.Phrase phrase, RuleEvaluation e) {
        for (PhraseMatcher.Phrase.MatchIterator i = phrase.itemIterator(); i.hasNext(); ) {
            i.next();
            FlattenedItem current = e.currentItem();
            if (current == null) return false;
            if (!labelMatches(e.currentItem().getItem(),e)) return false;
            if (!e.isInNegation())
                e.addMatch(current, i.getReplace());
            e.next();
        }
        if (phrase.getLength() > phrase.getBackedLength()) return false; // The underlying composite item has changed
        return true;
    }

    public void makeReferences(RuleBase ruleBase) {
        namedCondition = ruleBase.getCondition(conditionName);
        if (namedCondition == null) { // Then this may reference some automata value, if we have an automata
            if (ruleBase.usesAutomata())
                automataLookup = true;
            else
                throw new RuleBaseException("Referenced condition '" + conditionName +
                                            "' does not exist in " + ruleBase);
        }
    }

    protected boolean hasOpenChoicepoint(RuleEvaluation e) {
        if (namedCondition == null) return false;
        return namedCondition.getCondition().hasOpenChoicepoint(e);
    }

    protected boolean isDefaultContextName() {
        return getContextName() == null || getContextName().equals(conditionName);
    }

    @Override
    protected String toInnerString() {
        return "[" + conditionName + "]";
    }

}
