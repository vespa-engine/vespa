// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.jdisc.service.AbstractServerProvider;
import com.yahoo.jdisc.service.CurrentContainer;

/**
 * @author Christian Andersen
 */
public class MockServer extends AbstractServerProvider {

    private boolean started = false;

    public MockServer(CurrentContainer container) {
        super(container);
    }

    @Override
    public void start() {
        started = true;
    }

    @Override
    public void close() {

    }

    public boolean isStarted() {
        return started;
    }

}
