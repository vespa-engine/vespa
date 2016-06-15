// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.NoopSharedResource;
import com.yahoo.jdisc.service.ServerProvider;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public final class NonWorkingServerProvider extends NoopSharedResource implements ServerProvider {

    @Override
    public void start() {

    }

    @Override
    public void close() {

    }
}
