// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.engine.FlattenedItem;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * Superclass of all kinds of conditions of production rules
 *
 * @author bratseth
 */
public abstract class Condition {

    /** The parent of this condition, or null if this is not nested */
    private CompositeCondition parent = null;

    /**
     * The label of this condition, or null if none.
     * Specified by label:condition
     * The label is also the default context is no context is speficied explicitly
     */
    private String label;

    /**
     * The name space refered by this match, or null if the default (query)
     * Specified by namespace.condition in rules.
     */
    private String nameSpace = null;

    /**
     * The name of the context created by this, or null if none
     * Specified by context/condition in rules
     */
    private String contextName;

    /** Position constraints of the terms matched by this condition */
    private Anchor anchor = Anchor.NONE;

    public enum Anchor {
        NONE, START, END, BOTH;
        public static Anchor create(boolean start,boolean end) {
            if (start && end) return Anchor.BOTH;
            if (start) return Anchor.START;
            if (end) return Anchor.END;
            return NONE;
        }
    }

    public Condition() {
        this(null, null);
    }

    public Condition(String label) {
        this(label, null);
    }

    public Condition(String label, String context) {
        this.label = label;
        this.contextName = context;
    }

    /**
     * Sets the name whatever is matched by this condition can be refered as, or null
     * to make it nonreferable
     */
    public void setContextName(String contextName) { this.contextName = contextName; }

    /**
     * Returns the name whatever is matched by this condition can be referred as, or null
     * if it is unreferable
     */
    public String getContextName() { return contextName; }

    /** Returns whether this is referable, returns context!=null by default */
    protected boolean isReferable() { return contextName != null; }

    /** Sets the label of this. Set to null to use the default */
    public String getLabel() { return label; }

    /** Returns the label of this, or null if none (the default) */
    public void setLabel(String label) { this.label = label; }

    /** Returns the name of the namespace of this, or null if default (query) */
    public String getNameSpace() { return nameSpace; }

    /** Sets the name of the namespace of this */
    public void setNameSpace(String nameSpace) { this.nameSpace=nameSpace; }

    /** Returns the condition this is nested within, or null if it is not nested */
    public CompositeCondition getParent() { return parent; }

    /** Called by CompositeCondition.addCondition() */
    void setParent(CompositeCondition parent) { this.parent=parent; }

    /** Sets a positional constraint on this condition */
    public void setAnchor(Anchor anchor) { this.anchor = anchor; }

    /** Returns the positional constraint on this anchor. This is never null */
    public Anchor getAnchor() { return anchor; }

    /**
     * Returns whether this condition matches the given evaluation
     * at the <i>current</i> location of the evaluation. Calls the doesMatch
     * method of each condition subtype.
     */
    public final boolean matches(RuleEvaluation e) {
        // TODO: With this algoritm, each choice point will move to the next choice on each reevaluation
        // In the case where there are multiple ellipses, we may want to do globally coordinated
        // moves of all the choice points instead
        try {
            preMatchHook(e);

            if (!matchesStartAnchor(e)) return false;

            String higherLabel = e.getCurrentLabel();
            if (getLabel() != null)
                e.setCurrentLabel(getLabel());

            boolean matches = doesMatch(e);
            while ( ! matches && hasOpenChoicepoint(e)) {
                matches = doesMatch(e);
            }

            e.setCurrentLabel(higherLabel);

            if ( ! matchesEndAnchor(e)) return false;

            traceResult(matches, e);
            return matches;
        }
        finally {
            postMatchHook(e);
        }

    }

    /** Check start anchor. Trace level 4 if no match */
    protected boolean matchesStartAnchor(RuleEvaluation e) {
        if (anchor != Anchor.START && anchor != Anchor.BOTH) return true;
        if (e.getPosition() == 0) return true;
        if (e.getTraceLevel() >= 4)
            e.trace(4, this + " must be at the start, which " + e.currentItem() + " isn't");
        return false;
    }

    /** Check start anchor. Trace level 4 if no match */
    protected boolean matchesEndAnchor(RuleEvaluation e) {
        if (anchor != Anchor.END && anchor != Anchor.BOTH) return true;
        if (e.getPosition() >= e.items().size()) return true;
        if (e.getTraceLevel() >= 4)
            e.trace(4, this + " must be at the end, which " + e.currentItem() + " isn't");
        return false;
    }

    protected void traceResult(boolean matches, RuleEvaluation e) {
        if (matches && e.getTraceLevel() >= 3)
            e.trace(3, "Matched '" + this + "'" + getMatchInfoString(e) + " at " + e.previousItem());
        if (!matches && e.getTraceLevel() >= 4)
            e.trace(4, "Did not match '" + this + "' at " + e.currentItem());
    }

    protected String getMatchInfoString(RuleEvaluation e) {
        String matchInfo = getMatchInfo(e);
        if (matchInfo == null) return "";
        return " as '" + matchInfo + "'";
    }

    /**
     * Called when match is called, before anything else.
     * Always call super.preMatchHook when overriding.
     */
    protected void preMatchHook(RuleEvaluation e) {
        e.entering(contextName);
    }

    /**
     * Called just before match returns, on any return condition including exceptions.
     * Always call super.postMatchHook when overriding
     */
    protected void postMatchHook(RuleEvaluation e) {
        e.leaving(contextName);
    }

    /**
     * Override this to return a string describing what this condition has matched in this evaluation.
     * Will only be called when this condition is actually matched in this condition
     *
     * @return info about what is matched, or null if there is no info to return (default)
     */
    protected String getMatchInfo(RuleEvaluation e) { return null; }

    /**
     * Returns whether this condition matches the given evaluation
     * at the <i>current</i> location of the evaluation. If there is a
     * match, the evaluation must be advanced to the location beyond
     * the matching item(s) before this method returns.
     */
    protected abstract boolean doesMatch(RuleEvaluation e);

    /**
     * Returns whether there is an <i>open choice</i> in this or any of its subconditions.
     * Returns false by default, must be overriden by conditions which may generate
     * choices open accross multiple calls to matches, or contain such conditions.
     */
    protected boolean hasOpenChoicepoint(RuleEvaluation e) {
        return false;
    }

    /** Override if references needs to be set in this condition of its children */
    public void makeReferences(RuleBase rules) { }

    protected String getLabelString() {
        if (label == null) return "";
        return label + ":";
    }

    /** Whether the label matches the current item, true if there is no current item */
    protected boolean labelMatches(RuleEvaluation e) {
        FlattenedItem flattenedItem = e.currentItem();
        if (flattenedItem == null) return true;
        TermItem item = flattenedItem.getItem();
        if (item == null) return true;
        return labelMatches(item, e);
    }

    protected boolean labelMatches(TermItem evaluationTerm, RuleEvaluation e) {
        String indexName = evaluationTerm.getIndexName();
        String label = getLabel();
        if (label == null)
            label = e.getCurrentLabel();
        if ("".equals(indexName) && label == null) return true;
        if (indexName.equals(label)) return true;
        if (e.getTraceLevel() >= 4)
            e.trace(4, "'" + this + "' does not match, label of " + e.currentItem() + " was required to be " + label);
        return false;
    }

    /** All instances of this produces a parseable string output */
    protected abstract String toInnerString();

    protected boolean isDefaultContextName() { return false; }

    @Override
    public String toString() {
        String contextString = "";
        String nameSpaceString = "";
        if (contextName != null && !isDefaultContextName())
            contextString = contextName + "/";
        if (getNameSpace() != null)
            nameSpaceString = getNameSpace() + ".";
        return contextString + nameSpaceString + toInnerString();
    }

}
