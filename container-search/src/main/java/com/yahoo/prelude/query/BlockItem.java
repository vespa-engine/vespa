// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


/**
 * An interface used for anything which represents a single block of query input.
 *
 * @author Steinar Knutsen
 */
public interface BlockItem extends HasIndexItem {

    /**
     * The untransformed raw text from the user serving as base for
     * this item.
     */
    String getRawWord();

    /** Returns the substring which is the origin of this item, or null if none */
    public Substring getOrigin();

    /** Returns the value of this term as a string */
    public abstract String stringValue();

    /**
     * Is this block of text conceptually from the user query?
     */
    boolean isFromQuery();

    boolean isStemmed();

    /**
     * Does this item represent "usual words"?
     */
    boolean isWords();

    /**
     * If the block has to be resegmented, what operator should be chosen if it
     * is necessary to change operator?
     */
    SegmentingRule getSegmentingRule();

}
