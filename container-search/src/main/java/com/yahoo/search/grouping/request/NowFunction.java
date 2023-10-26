// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;
import java.util.Collections;

/**
 * This class represents a now-function in a {@link GroupingExpression}. It evaluates to a long that equals the number
 * of seconds since midnight, January 1, 1970 UTC.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class NowFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     */
    public NowFunction() {
        this(null, null);
    }

    private NowFunction(String label, Integer level) {
        super("now", label, level, Collections.emptyList());
    }

    @Override
    public NowFunction copy() {
        return new NowFunction(getLabel(), getLevelOrNull());
    }

}

