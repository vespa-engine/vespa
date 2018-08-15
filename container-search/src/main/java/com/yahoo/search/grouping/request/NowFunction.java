// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Collections;

/**
 * This class represents a now-function in a {@link GroupingExpression}. It evaluates to a long that equals the number
 * of seconds since midnight, January 1, 1970 UTC.
 *
 * @author Simon Thoresen Hult
 */
public class NowFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     */
    public NowFunction() {
        super("now", Collections.<GroupingExpression>emptyList());
    }
}

