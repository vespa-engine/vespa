// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.intent.model;

/**
 * An intent in an intent model tree. The intent node score is the <i>probability</i> of this intent
 * given the parent interpretation.
 *
 * @author <a href="mailto:bratseth@yahoo-inc.com">Jon Bratseth</a>
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
    public @Override String toString() {
        return intent + ":" + getScore();
    }

}
