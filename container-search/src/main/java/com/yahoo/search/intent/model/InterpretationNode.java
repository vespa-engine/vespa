// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.intent.model;

import com.yahoo.text.interpretation.Interpretation;

/**
 * An interpretation which may have multiple intents. The score of this node is the probability of
 * the wrapped interpretation.
 *
 * @author bratseth
 */
public class InterpretationNode extends ParentNode<IntentNode> {

    private Interpretation interpretation;

    public InterpretationNode(Interpretation interpretation) {
        super(0); // Super score is not used
        this.interpretation=interpretation;
        children().add(new IntentNode(Intent.Default,1.0));
    }

    /** Returns this interpretation. This is never null. */
    public Interpretation getInterpretation() { return interpretation; }

    /** Sets this interpretation */
    public void setInterpretation(Interpretation interpretation) {
        this.interpretation=interpretation;
    }

    /** Returns the probability of the interpretation of this */
    @Override
    public double getScore() {
        return interpretation.getProbability();
    }

    /** Sets the probability of the interpretation of this */
    public void setScore(double score) {
        interpretation.setProbability(score);
    }

    /** Returns interpretations toString() */
    @Override
    public String toString() {
        return interpretation.toString();
    }

}
