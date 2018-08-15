// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.search.grouping.Continuation;

/**
 * @author Simon Thoresen Hult
 */
abstract class EncodableContinuation extends Continuation {

    public abstract void encode(IntegerEncoder out);

    @Override
    public final String toString() {
        IntegerEncoder encoder = new IntegerEncoder();
        encode(encoder);
        return encoder.toString();
    }
}
