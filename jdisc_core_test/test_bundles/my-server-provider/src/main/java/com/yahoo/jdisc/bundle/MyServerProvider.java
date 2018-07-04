// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.bundle;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.service.ServerProvider;

import java.util.concurrent.CountDownLatch;

/**
 * @author Simon Thoresen Hult
 */
public class MyServerProvider extends AbstractResource implements ServerProvider {

    private final CountDownLatch startLatch;
    private final CountDownLatch closeLatch;
    private final CountDownLatch destroyLatch;

    @Inject
    public MyServerProvider(@Named("Init") CountDownLatch initLatch,
                            @Named("Start") CountDownLatch startLatch,
                            @Named("Close") CountDownLatch closeLatch,
                            @Named("Destroy") CountDownLatch destroyLatch)
    {
        this.startLatch = startLatch;
        this.closeLatch = closeLatch;
        this.destroyLatch = destroyLatch;
        initLatch.countDown();
    }

    @Override
    public void start() {
        startLatch.countDown();
    }

    @Override
    public void close() {
        closeLatch.countDown();
    }

    @Override
    protected void destroy() {
        destroyLatch.countDown();
    }
}
