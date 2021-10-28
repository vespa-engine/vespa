// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


/**
 * An interface used for anything which represents a single block of query input.
 *
 * @author Steinar Knutsen
 */
public interface BlockItem extends HasIndexItem {

    /** The untransformed raw text from the user serving as base for this item. */
    String getRawWord();

    /** Returns the substring which is the origin of this item, or null if none */
    Substring getOrigin();

    /** Returns the value of this term as a string */
    String stringValue();

    /**
     * Returns whether this block of text originates from a user and should therefore
     * receive the normal processing applied to raw text (such as stemming).
     */
    boolean isFromQuery();

    boolean isStemmed();

    /** Returns whether this item represents normal text */
    boolean isWords();

    /**
     * If the block has to be resegmented, what operator should be chosen if it
     * is necessary to change operator?
     */
    SegmentingRule getSegmentingRule();

}
