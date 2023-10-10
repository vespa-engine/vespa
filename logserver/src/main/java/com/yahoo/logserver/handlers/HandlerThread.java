// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.yahoo.io.FatalErrorHandler;
import java.util.logging.Level;
import com.yahoo.log.LogMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This handler implements a dispatcher which runs in its own
 * thread.  The purpose of this handler is to isolate execution
 * of handlers from the main server IO threads.
 *
 * @author Bjorn Borud
 */

public class HandlerThread extends Thread implements LogHandler {
    private static final Logger log = Logger.getLogger(HandlerThread.class.getName());

    // default queue size is 200
    public static final int DEFAULT_QUEUESIZE = 200;
    private static int queueSize = DEFAULT_QUEUESIZE;

    private FatalErrorHandler fatalErrorHandler;

    // set other queue size if specified
    static {
        String queueSizeStr = System.getProperty("logserver.queue.size");
        if (queueSizeStr != null) {
            queueSize = Integer.parseInt(queueSizeStr);

            // should never be smaller than 50
            if (queueSize < 50) {
                queueSize = 50;
            }

            log.info("set queue size to " + queueSize);
        }
    }

    private static class ItemOrList {
        final LogMessage item;
        final List<LogMessage> list;

        ItemOrList(LogMessage i) {
            this.item = i;
            this.list = null;
        }

        ItemOrList(List<LogMessage> l) {
            this.item = null;
            this.list = l;
        }

        public String toString() {
            return "item=" + item + ", list=" + list;
        }
    }

    private final BlockingQueue<ItemOrList> queue;
    private final List<LogHandler> handlers = new ArrayList<>();
    public HandlerThread(String name) {
        super(name);
        queue = new LinkedBlockingQueue<>(queueSize);
        log.log(Level.CONFIG, "logserver.queue.size=" + queueSize);
    }

    /**
     * Register a handler for fatal errors.
     *
     * @param f The FatalErrorHandler instance to be registered
     */
    public synchronized void setFatalErrorHandler(FatalErrorHandler f) {
        fatalErrorHandler = f;
    }

    /**
     * Called by the LogDispatch to put a LogMessage onto the Queue
     *
     * @param message The LogMessage we wish to dispatch to this
     *                handler thread.
     */
    public void handle(LogMessage message) {
        handleInternal(new ItemOrList(message));
    }

    /**
     * Called by the LogDispatch to put a list of LogMessage
     * instances onto the Queue.
     */
    public void handle(List<LogMessage> messages) {
        handleInternal(new ItemOrList(messages));
    }

    private void handleInternal(ItemOrList o) {
        boolean done = false;
        while (! done) {
            try {
                queue.put(o);
                done = true;
            } catch (InterruptedException e) {
                // NOP
            }
        }
    }

    public void flush() {
        for (LogHandler handler : handlers) {
            handler.flush();
        }
    }

    public void close() {
        for (LogHandler handler : handlers) {
            handler.close();
        }
    }

    /**
     * Register a LogHandler
     */
    public synchronized void registerHandler(LogHandler handler) {
        log.fine("Registering handler " + handler);
        handlers.add(handler);
    }

    /**
     * Unregister a Loghandler
     */
    public synchronized void unregisterHandler(LogHandler handler) {
        int idx;
        while ((idx = handlers.indexOf(handler)) != - 1) {
            try {
                handlers.remove(idx);
            } catch (IndexOutOfBoundsException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Return an array of the registered handlers.
     *
     * @return Returns an array of the handlers registered
     */
    public LogHandler[] getHandlers() {
        LogHandler[] h = new LogHandler[handlers.size()];
        return handlers.toArray(h);
    }


    /**
     * Return the underlying queue used to send LogMessage instances
     * to this handler thread.
     */
    public BlockingQueue<ItemOrList> getQueue() {
        return queue;
    }

    /**
     * Consume messages from the incoming queue and hand
     * them off to the handlers.
     */
    public void run() {
        if (queue == null) {
            throw new NullPointerException("channel is not allowed to be null");
        }

        // TODO: Make the logmessage elements some kind of composite structure to handle both individual messages and lists uniformly.
        List<ItemOrList> drainList = new ArrayList<>(queue.size() + 1);
        try {
            for (; ; ) {
                drainList.clear();
                // block in take(), then see if there is more
                // to be had with drainTo()
                drainList.add(queue.take());
                queue.drainTo(drainList);

                for (int i = 0; i < drainList.size(); i++) {
                    // we can get two types of elements here: single log
                    // messages or lists of log messages, so we need to
                    // handle them accordingly.
                    ItemOrList o = drainList.get(i);
                    drainList.set(i, null);

                    if (o.item != null) {
                        for (LogHandler handler : handlers) {
                            handler.handle(o.item);
                        }
                    } else if (o.list != null) {
                        for (LogHandler handler : handlers) {
                            handler.handle(o.list);
                        }
                    } else {
                        throw new IllegalArgumentException("not LogMessage or List: " + o);
                    }
                }
            }
        } catch (InterruptedException e) {
            // NOP
        } catch (Throwable t) {
            if (fatalErrorHandler != null) {
                fatalErrorHandler.handle(t, null);
            }
        } finally {
            log.fine("Handler thread "
                             + getName()
                             + " exiting, removing handlers");
            for (LogHandler handler : handlers) {
                log.fine("Removing handler " + handler);
                handler.close();
            }
            handlers.clear();
            log.fine("Handler thread " + getName() + " done");
        }

    }
}
