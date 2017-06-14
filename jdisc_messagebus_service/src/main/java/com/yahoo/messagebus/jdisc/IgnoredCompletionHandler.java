// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.yahoo.jdisc.handler.CompletionHandler;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
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
