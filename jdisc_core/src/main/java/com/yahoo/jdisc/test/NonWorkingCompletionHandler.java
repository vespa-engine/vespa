// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.handler.CompletionHandler;

/**
 * @author Simon Thoresen Hult
 */
public final class NonWorkingCompletionHandler implements CompletionHandler {

    @Override
    public void completed() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void failed(Throwable t) {
        throw new UnsupportedOperationException();
    }
}
