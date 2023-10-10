// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.intent.model;

import java.util.Map;

/**
 * A source node in an intent model tree. Represents a source with an appropriateness score
 * (i.e the score of a source node is called <i>appropriateness</i>).
 * Sources are ordered by decreasing appropriateness.
 *
 * @author bratseth
 */
public class SourceNode extends Node {

    private Source source;

    public SourceNode(Source source,double score) {
        super(score);
        this.source=source;
    }

    /** Sets the source of this node */
    public void setSource(Source source) { this.source=source; }

    /** Returns the source of this node */
    public Source getSource() { return source; }

    @Override void addSources(double weight,Map<Source,SourceNode> sources) {
        SourceNode existing=sources.get(source);
        if (existing!=null)
            existing.increaseScore(weight*getScore());
        else
            sources.put(source,new SourceNode(source,weight*getScore()));
    }

    /** Returns source:appropriateness */
    @Override
    public String toString() {
        return source + ":" + getScore();
    }

}
