// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver;

import com.yahoo.logserver.handlers.LogHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Bjorn Borud
 */
public class Flusher extends Thread {
    private static final Logger log = Logger.getLogger(Flusher.class.getName());
    private static final Flusher instance;
    private static final List<WeakReference<LogHandler>> logHandlers =
            new ArrayList<WeakReference<LogHandler>>();

    static {
        instance = new Flusher();
        instance.start();
    }

    Flusher() {
        super("flusher");
    }

    public static synchronized void register(LogHandler logHandler) {
        logHandlers.add(new WeakReference<>(logHandler));
    }

    @Override
    public synchronized void run() {
        try {
            while(!isInterrupted()) {
                Iterator<WeakReference<LogHandler>> it = logHandlers.iterator();
                while (it.hasNext()) {
                    WeakReference<LogHandler> r = it.next();
                    LogHandler h = r.get();
                    if (h == null) {
                        it.remove();
                    } else {
                        h.flush();
                    }
                    if (log.isLoggable(Level.FINE)) {
                        log.log(Level.FINE, "Flushing " + h);
                    }
                }
                Thread.sleep(2000);
            }
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "flusher was interrupted", e);
        }
    }
}
