// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import org.junit.Test;


import java.util.concurrent.ThreadFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
/**
 * @author baldersheim
 */
public class ThreadFactoryFactoryTest {

    static class Runner implements Runnable {
        @Override
        public void run() {

        }
    }

    @Test
    public void requireThatFactoryCreatesCorrectlyNamedThreads() {
        Thread thread = ThreadFactoryFactory.getThreadFactory("a").newThread(new Runner());
        assertEquals("a-1-thread-1", thread.getName());
        thread = ThreadFactoryFactory.getThreadFactory("a").newThread(new Runner());
        assertEquals("a-2-thread-1", thread.getName());
        thread = ThreadFactoryFactory.getThreadFactory("b").newThread(new Runner());
        assertEquals("b-1-thread-1", thread.getName());
        ThreadFactory factory =  ThreadFactoryFactory.getThreadFactory("a");
        thread = factory.newThread(new Runner());
        assertEquals("a-3-thread-1", thread.getName());
        thread = factory.newThread(new Runner());
        assertEquals("a-3-thread-2", thread.getName());
        thread = factory.newThread(new Runner());
        assertEquals("a-3-thread-3", thread.getName());
    }

}
