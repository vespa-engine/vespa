// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.intent.model;

/**
 * An intent in an intent model tree. The intent node score is the <i>probability</i> of this intent
 * given the parent interpretation.
 *
 * @author bratseth
 */
public class IntentNode extends ParentNode<SourceNode> {

    private Intent intent;

    public IntentNode(Intent intent,double probabilityScore) {
        super(probabilityScore);
        this.intent=intent;
    }

    /** Returns the intent of this node, this is never null */
    public Intent getIntent() { return intent; }

    public void setIntent(Intent intent) { this.intent=intent; }

    /** Returns intent:probability */
    @Override
    public String toString() {
        return intent + ":" + getScore();
    }

}
