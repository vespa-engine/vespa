// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.client;

import com.google.inject.Inject;
import com.yahoo.jdisc.application.AbstractApplication;
import com.yahoo.jdisc.application.BundleInstaller;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.service.CurrentContainer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * <p>This is a convenient parent class for {@link ClientApplication} developers. It extends {@link AbstractApplication}
 * and implements {@link Runnable} to wait for {@link #shutdown()} to be called. When using this class, you implement
 * {@link #start()} (and optionally {@link #stop()}), and provide a reference to it to whatever component is responsible
 * for signaling shutdown.</p>
 *
 * @author Simon Thoresen Hult
 */
public abstract class AbstractClientApplication extends AbstractApplication implements ClientApplication {

    private final CountDownLatch done = new CountDownLatch(1);

    @Inject
    public AbstractClientApplication(BundleInstaller bundleInstaller, ContainerActivator activator,
                                     CurrentContainer container) {
        super(bundleInstaller, activator, container);
    }

    @Override
    public final void run() {
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public final void shutdown() {
        done.countDown();
    }

    public final boolean isShutdown() {
        return done.getCount() == 0;
    }

    public final boolean awaitShutdown(int timeout, TimeUnit unit) throws InterruptedException {
        return done.await(timeout, unit);
    }

    public final void awaitShutdown() throws InterruptedException {
        done.await();
    }
}
