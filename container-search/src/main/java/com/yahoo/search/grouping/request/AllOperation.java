// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This is a grouping operation that processes the input list as a whole, as opposed to {@link EachOperation} which
 * processes each element of that list separately.
 *
 * @author Simon Thoresen Hult
 */
public class AllOperation extends GroupingOperation {

    /**
     * Constructs a new instance of this class.
     */
    public AllOperation() {
        super("all");
    }
}
