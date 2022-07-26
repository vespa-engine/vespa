// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author bjorncs
 */
public class Threads {

    private Threads() {}

    /** Returns all threads in JVM */
    public static Collection<Thread> getAllThreads() {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        ThreadGroup parent;
        while ((parent = root.getParent()) != null) {
            root = parent;
        }
        // The number of threads may increase between activeCount() and enumerate()
        Thread[] threads = new Thread[root.activeCount() + 100];
        int count;
        while ((count = root.enumerate(threads, true)) == threads.length) {
            threads = new Thread[threads.length + 1000];
        }
        return List.of(Arrays.copyOf(threads, count));
    }
}
