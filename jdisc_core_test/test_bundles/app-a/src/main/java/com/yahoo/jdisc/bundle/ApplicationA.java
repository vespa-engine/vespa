// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.bundle;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.yahoo.jdisc.application.Application;

import java.util.concurrent.CountDownLatch;

/**
 * @author Simon Thoresen Hult
 */
public class ApplicationA implements Application {

    private final CountDownLatch startLatch;
    private final CountDownLatch stopLatch;
    private final CountDownLatch destroyLatch;

    @Inject
    public ApplicationA(@Named("Init") CountDownLatch initLatch,
                        @Named("Start") CountDownLatch startLatch,
                        @Named("Stop") CountDownLatch stopLatch,
                        @Named("Destroy") CountDownLatch destroyLatch)
    {
        this.startLatch = startLatch;
        this.stopLatch = stopLatch;
        this.destroyLatch = destroyLatch;
        initLatch.countDown();
    }

    @Override
    public void start() {
        startLatch.countDown();
    }

    @Override
    public void stop() {
        stopLatch.countDown();
    }

    @Override
    public void destroy() {
        destroyLatch.countDown();
    }
}
