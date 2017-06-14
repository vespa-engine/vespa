// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.bundle;

import com.yahoo.jdisc.service.AbstractServerProvider;
import com.yahoo.jdisc.service.CurrentContainer;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
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
