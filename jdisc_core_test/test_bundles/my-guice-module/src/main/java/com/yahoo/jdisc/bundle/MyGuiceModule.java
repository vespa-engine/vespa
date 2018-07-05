// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.bundle;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.util.concurrent.CountDownLatch;

/**
 * @author Simon Thoresen Hult
 */
public class MyGuiceModule extends AbstractModule {

    private final CountDownLatch configLatch;

    @Inject
    public MyGuiceModule(@Named("Init") CountDownLatch initLatch,
                         @Named("Config") CountDownLatch configLatch)
    {
        this.configLatch = configLatch;
        initLatch.countDown();
    }

    @Override
    protected void configure() {
        configLatch.countDown();
    }
}
