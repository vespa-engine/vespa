// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4.mplex;

import com.yahoo.io.FatalErrorHandler;
import com.yahoo.io.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pool of com.yahoo.io.Listener instances for shared use by Vespa backend
 * searchers.
 *
 * @author baldersheim
 * @since 5.3.0
 */
public final class ListenerPool {
    private final static Logger logger = Logger.getLogger(ListenerPool.class.getName());
    private final List<Listener> listeners;

    public ListenerPool(String name, int numListeners) {
        listeners = new ArrayList<>(numListeners);
        FatalErrorHandler fatalErrorHandler = new FatalErrorHandler();
        for (int i = 0; i < numListeners; i++) {
            Listener listener = new Listener(name + "-" + i);
            listener.setFatalErrorHandler(fatalErrorHandler);
            listener.start();
            listeners.add(listener);
        }
    }

    public Listener get(int index) {
        return listeners.get(index);
    }

    public int size() {
        return listeners.size();
    }

    public void close() {
        for (Listener listener : listeners) {
            listener.interrupt();
        }
        try {
            for (Listener listener : listeners) {
                listener.join();
            }
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Got interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

}
