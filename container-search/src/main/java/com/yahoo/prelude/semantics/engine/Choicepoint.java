// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.engine;

import com.yahoo.prelude.semantics.rule.Condition;

/**
 * A choice point in an rule evaluation. A choicepoint is open if there are other choices to make at the point,
 * closed if there are no further choices. In addition it contains enough information to enable
 * the rule evaluation to backtrack to this point
 *
 * @author bratseth
 */
public class Choicepoint {

    /** Whether there are (or may be) open choices to explore at this choicepoint yet */
    private boolean open=true;

    /** The number of tries made at this choice point */
    private int tries=0;

    /** The condition creating this choicepoint */
    private Condition condition;

    /** The state this choice point can be rolled back to */
    private State state;

    private RuleEvaluation owner;

    public Choicepoint(RuleEvaluation e, Condition condition) {
        this.owner=e;
        state=new State(this,e);
        this.condition=condition;
        if (e.getTraceLevel()>=5)
            e.trace(5,"Added choice point at " + e.currentItem() + " for '" + condition + "'");
    }

    /** Returns the condition which created this choice point */
    public Condition getCondition() { return condition; }

    /** Returns wether there are (or may be) open choices to explore at this choicepoint yet */
    public boolean isOpen() { return open; }

    /** Marks this choice point as closed (!open) - there are no further choices to explore */
    public void close() { this.open=false; }

    /** Returns the number open tries made at this point */
    public int tryCount() { return tries; }

    /** Registers that another try has been made */
    public void addTry() {
        tries++;
    }

    /**
     * Backtrack to the evaluation state at the point where this choicepoint were instantiated.
     */
    public void backtrack() {
        state.backtrack(owner);
        if (owner.getTraceLevel()>=5)
            owner.trace(5,"Backtracked to " + owner.currentItem() + " for '" + condition + "'");
    }

    /** Backtracks the position only, not matches */
    public void backtrackPosition() {
        state.backtrackPosition(owner);
    }

    /**
     * Updates the state of this choice point to the current state of its evaluation
     */
    public void updateState() {
        state.updateState(owner);
    }

    /** Returns the state of this choice point */
    public State getState() { return state; }

    /** The state of this choicepoint */
    public final static class State {

        private int position=0;

        private int referencedMatchCount=0;

        private int nonreferencedMatchCount=0;

        public State(Choicepoint choicepoint,RuleEvaluation evaluation) {
            updateState(evaluation);
        }

        public void updateState(RuleEvaluation evaluation) {
            position=evaluation.currentPosition();
            referencedMatchCount=evaluation.getReferencedMatchCount();
            nonreferencedMatchCount=evaluation.getNonreferencedMatchCount();
        }

        /** Backtrack to the evaluation state at the point where this choicepoint were instantiated */
        public void backtrack(RuleEvaluation e) {
            backtrackPosition(e);

            // Is this check masking errors?
            if (e.referencedMatches().size()>referencedMatchCount)
                e.referencedMatches().subList(referencedMatchCount,
                        e.referencedMatches().size())
                        .clear();
            // Is this check masking errors?
            if (e.nonreferencedMatches().size()>nonreferencedMatchCount)
                e.nonreferencedMatches().subList(nonreferencedMatchCount,
                        e.nonreferencedMatches().size())
                        .clear();
        }

        public void backtrackPosition(RuleEvaluation e) {
            e.setPosition(position);
        }

        public int getPosition() { return position; }

        public int getReferencedMatchCount() { return referencedMatchCount; }

        public int getNonreferencedMatchCount() { return nonreferencedMatchCount; }

    }

}

