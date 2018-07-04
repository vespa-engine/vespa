// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.search.grouping.Continuation;

/**
 * @author Simon Thoresen Hult
 */
public class ContinuationDecoder {

    public static Continuation decode(String str) {
        return CompositeContinuation.decode(new IntegerDecoder(str));
    }
}
