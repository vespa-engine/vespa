// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * A simple thread factory that decorates <code>Executors.defaultThreadFactory()</code>
 * and sets all created threads to be daemon threads.
 *
 * @author Einar M R Rosenvinge
 */
public class DaemonThreadFactory implements ThreadFactory {

    private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();
    private String prefix = null;

    /**
     * Creates a deamon thread factory that creates threads with the default names
     * provided by <code>Executors.defaultThreadFactory()</code>.
     */
    public DaemonThreadFactory() {
    }

    /**
     * Creates a deamon thread factory that creates threads with the default names
     * provided by <code>Executors.defaultThreadFactory()</code> prepended by the
     * specified prefix.
     *
     * @param prefix the thread name prefix to use
     */
    public DaemonThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread t = defaultThreadFactory.newThread(runnable);
        t.setDaemon(true);
        if (prefix != null) {
            t.setName(prefix + t.getName());
        }
        return t;
    }

}
