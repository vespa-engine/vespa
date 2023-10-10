// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * <p>This class decorates {@link Thread} to allow for internal jDISC optimizations. Whenever possible a jDISC
 * application should use this class instead of Thread. The {@link ContainerThread.Factory} class is a helper-class for
 * working with the {@link Executors} framework.</p>
 *
 * @author Simon Thoresen Hult
 */
public class ContainerThread extends Thread {

    private final MetricConsumer consumer;

    /**
     * Allocates a new ContainerThread object. This constructor calls the parent {@link Thread#Thread(Runnable)}
     * constructor.
     *
     * @param target   the object whose <code>run</code> method is called.
     * @param consumer the MetricConsumer of this thread.
     */
    public ContainerThread(Runnable target, MetricConsumer consumer) {
        super(target);
        this.consumer = consumer;
    }

    /**
     * <p>Returns the {@link MetricConsumer} of this. Note that this may be null.</p>
     *
     * @return The MetricConsumer of this, or null.
     */
    public MetricConsumer consumer() {
        return consumer;
    }

    /**
     * This class implements the {@link ThreadFactory} interface on top of a {@link Provider} for {@link
     * MetricConsumer} instances.
     */
    public static class Factory implements ThreadFactory {

        private final Provider<MetricConsumer> provider;

        @Inject
        public Factory(Provider<MetricConsumer> provider) {
            this.provider = provider;
        }

        @Override
        public Thread newThread(Runnable target) {
            return new ContainerThread(target, provider.get());
        }

    }

}
