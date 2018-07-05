// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.NoopSharedResource;
import com.yahoo.jdisc.service.ServerProvider;

/**
 * @author Simon Thoresen Hult
 */
public final class NonWorkingServerProvider extends NoopSharedResource implements ServerProvider {

    @Override
    public void start() {

    }

    @Override
    public void close() {

    }
}
