// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.bundle;

import com.yahoo.jdisc.service.AbstractServerProvider;
import com.yahoo.jdisc.service.CurrentContainer;

/**
 * @author Simon Thoresen Hult
 */
public class MyService extends AbstractServerProvider {

    public MyService(CurrentContainer container) {
        super(container);
    }

    @Override
    public void start() {

    }

    @Override
    public void close() {

    }
}
