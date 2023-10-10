// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.yahoo.jdisc.handler.CompletionHandler;

/**
 * @author Simon Thoresen Hult
 */
enum IgnoredCompletionHandler implements CompletionHandler {

    INSTANCE;

    @Override
    public void completed() {

    }

    @Override
    public void failed(final Throwable t) {

    }
}
