// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.*;
import com.yahoo.clientmetrics.RouteMetricSet;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * This class allows multiple clients to use one shared messagebus session.
 * The user should create a ResultCallback, which acts as a "session" for that
 * client, and send one or more messages using the send() methods.
 * When done sending messages, the client can wait for all messages to be replied to
 * using the waitForPending method.
 */
public class SharedSender implements ReplyHandler {

    public static final Logger log = Logger.getLogger(SharedSender.class.getName());

    private SendSession sender;

    public static class PendingMap {
        private static class Pending {
            public int value = 1;
        }

        private Map<ResultCallback, Pending> map = new HashMap<>();

        public int postIncrement(ResultCallback owner) {
            synchronized(map) {
                Pending p = map.get(owner);
                if (p != null) {
                    synchronized(p) {
                        return p.value++;
                    }
                } else {
                    map.put(owner, new Pending());
                    return 0;
                }
            }
        }

        public void decrement(ResultCallback owner) {
            synchronized(map) {
                Pending p = map.get(owner);
                if (p == null) {
                    // IllegalArgumentException e = new IllegalArgumentException("owner "+owner+" not in map");
                    // e.fillInStackTrace();
                    // e.printStackTrace();
                    return;
                }
                synchronized(p) {
                    if (--p.value == 0) {
                        map.remove(owner);
                        map.notify();
                    }
                    p.notifyAll();
                }
            }
        }

        public void waitForPending(ResultCallback owner, int threshold)
            throws InterruptedException
        {
            assert threshold >= 0;
            Pending p;
            synchronized(map) {
                p = map.get(owner);
            }
            if (p == null) {
                return;
            }
            synchronized(p) {
                while (p.value > threshold) {
                    p.wait();
                }
            }
        }

        public void waitForPending(ResultCallback owner, int threshold, long millis)
            throws InterruptedException
        {
            assert threshold >= 0;
            Pending p;
            synchronized(map) {
                p = map.get(owner);
            }
            if (p == null) {
                return;
            }
            synchronized(p) {
                // yes this should be an "if":
                if (p.value > threshold) {
                    p.wait(millis);
                }
            }
        }

        public void drain() throws InterruptedException {
            synchronized(map) {
                while (! map.isEmpty()) {
                    map.wait(100);
                }
            }
        }

        public int getValue(ResultCallback owner) {
            synchronized(map) {
                Pending p = map.get(owner);
                if (p == null) {
                    return 0;
                } else {
                    return p.value;
                }
            }
        }
    }

    private final PendingMap pendingMap = new PendingMap();
    private RouteMetricSet metrics;

    // Maps from filename to number of pending requests
    private Map<ResultCallback, Integer> activeOwners = new HashMap<>();

    /**
     * Creates a new shared sender.
     * If oldsender != null, we copy that status information from that sender.
     */
    public SharedSender(String route, SessionFactory factory, SharedSender oldSender, Metric metric) {
        if (factory != null) {
            sender = factory.createSendSession(this, metric);
        }

        if (oldSender != null) {
            this.metrics = oldSender.metrics;
        } else {
            metrics = new RouteMetricSet(route, null);
        }
    }

    public RouteMetricSet getMetrics() {
        return metrics;
    }

    /// not used
    @Deprecated
    public void remove(ResultCallback owner) {
        while (pendingMap.getValue(owner) > 0) {
            pendingMap.decrement(owner);
        }
    }

    public void shutdown() {
        try {
            pendingMap.drain();
        } catch (InterruptedException e) {
        }
        sender.close();
    }

    /**
     * Waits until there are no more pending documents
     * for the given callback, or the timeout expires.
     *
     * @param owner     The callback to check for.
     * @param timeoutMs The number of milliseconds to wait, or -1 to wait indefinitely.
     * @return true if there were no more pending, or false if the timeout expired.
     */
    public boolean waitForPending(ResultCallback owner, long timeoutMs) {
        try {
            if (timeoutMs == -1 ) {
                pendingMap.waitForPending(owner, 0);
                return true;
            }
            long timeStart = SystemTimer.INSTANCE.milliTime();
            long timeLeft = timeoutMs;
            while (timeLeft > 0 && pendingMap.getValue(owner) > 0) {
                pendingMap.waitForPending(owner, 0, timeLeft);
                long elapsed = SystemTimer.INSTANCE.milliTime() - timeStart;
                timeLeft = timeoutMs - elapsed;
            }
        } catch (InterruptedException e) {
        }
        return pendingMap.getValue(owner) == 0;
    }

    public int getPendingCount(ResultCallback owner) {
        return pendingMap.getValue(owner);
    }

    /**
     * Returns true if the given result callback has any pending messages with this
     * sender.
     *
     * @param owner The callback to check
     * @return True if there are any pending, false if not.
     */
    public boolean hasPending(ResultCallback owner) {
        return getPendingCount(owner) > 0;
    }

    /**
     * Waits until the given file has no pending documents.
     * NOTE: Not used anywhere, deprecated.
     *
     * @param owner the file to check for pending documents
     */
    @Deprecated
    public void waitForPending(ResultCallback owner) {
        try {
            pendingMap.waitForPending(owner, 0);
        } catch (InterruptedException e) {
        }
    }

    /**
     * Sends a message
     *
     * @param msg   The message to send.
     * @param owner A callback to send replies to when received from messagebus
     */
    public void send(Message msg, ResultCallback owner) {
        send(msg, owner, -1, true);
    }

    /**
     * Sends a message. Waits until the number of pending messages for this owner has
     * become lower than the specified limit if necessary.
     *
     * @param msg                The message to send
     * @param owner              The callback to send replies to when received from messagebus
     * @param maxPendingPerOwner The maximum number of pending messages the callback
     * @param blockingQueue      If true, block until the message bus queue is available.
     */
    public void send(Message msg, ResultCallback owner, int maxPendingPerOwner, boolean blockingQueue) {
        // Silently fail messages that are attempted sent after the callback aborted.
        if (owner.isAborted()) {
            return;
        }
        try {
            int count = pendingMap.postIncrement(owner);
            if (maxPendingPerOwner != -1 && blockingQueue) {
                while (count > maxPendingPerOwner) {
                    pendingMap.decrement(owner); // could not send now, back off our try
                    log.log(LogLevel.INFO, "Owner " + owner + " already has " + count + " pending. Waiting for replies");
                    pendingMap.waitForPending(owner, maxPendingPerOwner);
                    count = pendingMap.postIncrement(owner);
                }
            }
            msg.setContext(owner);
            com.yahoo.messagebus.Result r = sender.send(msg, blockingQueue);
            if (!r.isAccepted()) {
                EmptyReply reply = new EmptyReply();
                msg.swapState(reply);
                reply.setMessage(msg);
                reply.addError(r.getError());
                handleReply(reply);
            }
        } catch (InterruptedException e) {
        }
    }

    /**
     * Implement replyHandler from messagebus. Called when a reply is received from messagebus.
     * Tries to find the callback from the reply context and updates the pending state for the callback.
     *
     * @param r the reply to process.
     */
    @Override
    public void handleReply(Reply r) {
        ResultCallback owner = (ResultCallback) r.getContext();

        if (owner != null) {
            metrics.addReply(r);
            int count = pendingMap.getValue(owner);
            owner.handleReply(r, count - 1);
            pendingMap.decrement(owner);
        } else {
            log.log(LogLevel.WARNING, "Received reply " + r + " for message " + r.getMessage() + " without context");
        }
    }

    public interface ResultCallback {

        /** Return true if we should continue waiting for replies for this sender. */
        boolean handleReply(Reply r, int numPending);

        /**
         * Returns true if feeding has been aborted. No more feeding is allowed with this
         * callback after that.
         */
        boolean isAborted();

    }

}
