// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a document checksum in a {@link GroupingExpression}. It evaluates to the YMUM checksum of the
 * input {@link com.yahoo.search.result.Hit}.
 *
 * @author Simon Thoresen Hult
 */
public class YmumValue extends DocumentValue {

    /**
     * Constructs a new instance of this class.
     */
    public YmumValue() {
        super("ymum()");
    }
}

