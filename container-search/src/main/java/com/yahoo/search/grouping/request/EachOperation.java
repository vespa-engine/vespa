// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This is a grouping operation that processes each element of the input list separately, as opposed to {@link
 * AllOperation} which processes that list as a whole.
 *
 * @author Simon Thoresen Hult
 */
public class EachOperation extends GroupingOperation {

    /**
     * Constructs a new instance of this class.
     */
    public EachOperation() {
        super("each");
    }

    @Override
    public void resolveLevel(int level) {
        if (level == 0) {
            throw new IllegalArgumentException("Operation '" + this + "' can not operate on " + getLevelDesc(level) + ".");
        }
        super.resolveLevel(level - 1);
    }
}
