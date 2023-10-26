// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.search.grouping.vespa.ContinuationDecoder;

/**
 * <p>This class represents a piece of data stored by the grouping framework within a grouping result, which can
 * subsequently be sent back along with the original request to navigate across a large result set. It is an opaque
 * data object that is not intended to be human readable.</p>
 *
 * <p>To render a continuation within a result set, you simply need to call {@link #toString()}.</p>
 *
 * @author Simon Thoresen Hult
 */
public abstract class Continuation {

    public static final String NEXT_PAGE = "next";
    public static final String PREV_PAGE = "prev";
    public static final String THIS_PAGE = "this";

    public static Continuation fromString(String string) {
        return ContinuationDecoder.decode(string);
    }

    /** Returns a deep copy of this */
    public abstract Continuation copy();

}
