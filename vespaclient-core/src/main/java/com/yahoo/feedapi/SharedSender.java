// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.*;
import com.yahoo.clientmetrics.RouteMetricSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final Object monitor = new Object();
    private RouteMetricSet metrics;

    private ConcurrentHashMap<ResultCallback, OwnerState> activeOwners = new ConcurrentHashMap<>();

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

    public void remove(ResultCallback owner) {
        activeOwners.remove(owner);
    }

    public void shutdown() {
        try {
            while ( ! activeOwners.isEmpty()) {
                Thread.sleep(10);
            }
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
        OwnerState state = activeOwners.get(owner);
        if (state != null) {
            try {
                return state.waitPending(timeoutMs);
            } catch (InterruptedException e) {
                return false;
            }
        }

        return true;
    }

    private OwnerState getNonNullState(ResultCallback owner) {
        OwnerState state = activeOwners.get(owner);
        if (state == null) {
            throw new IllegalStateException("No active callback : " + owner.toString());
        }
        return state;
    }

    public int getPendingCount(ResultCallback owner) {
        OwnerState state = activeOwners.get(owner);
        return (state != null) ? state.getNumPending() : 0;
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
     *
     * @param owner the file to check for pending documents
     */
    public void waitForPending(ResultCallback owner) {
        OwnerState state = activeOwners.get(owner);
        if (state != null) {
            try {
                state.waitPending();
            } catch (InterruptedException e) { }
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

        OwnerState state = activeOwners.get(owner);
        if (state == null) {
            state = new OwnerState();
            activeOwners.put(owner, state);
        }
        if (maxPendingPerOwner != -1 && blockingQueue) {
            state.waitMaxPendingbelow(maxPendingPerOwner);
        }

        state.addPending(1);
        msg.setContext(owner);

        try {
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
            boolean active = owner.handleReply(r);
            OwnerState state = activeOwners.get(owner);

            if (state != null) {
                if (log.isLoggable(LogLevel.SPAM)) {
                    log.log(LogLevel.SPAM, "Received reply for file " + owner.toString() + ", count was " + state.getNumPending());
                }
                if (!active) {
                    state.clearPending();
                    activeOwners.remove(owner);
                } else if ((state.decPending(1) <= 0)) {
                    activeOwners.remove(owner);
                }
            } else {
                // TODO: should be debug level if at all.
                log.log(LogLevel.WARNING, "Owner " + owner.toString() + " is not active");
            }
        } else {
            log.log(LogLevel.WARNING, "Received reply " + r + " for message " + r.getMessage() + " without context");
        }
    }


    public static class OwnerState {

        final AtomicInteger numPending = new AtomicInteger(0);

        int addPending(int count) {
            return numPending.addAndGet(count);
        }

        int decPending(int count) {
            int newValue = numPending.addAndGet(-count);
            if (newValue <= 0) {
                synchronized (numPending) {
                    numPending.notify();
                }
            }
            return newValue;
        }

        void waitMaxPendingbelow(int limit) {
            try {
                synchronized (numPending) {
                    while (numPending.get() > limit) {
                        numPending.wait(5);
                    }
                }
            } catch (InterruptedException e) {
            }
        }

        int getNumPending() {
            return numPending.get();
        }

        void clearPending() {
            numPending.set(0);
            synchronized (numPending) {
                numPending.notify();
            }
        }

        boolean waitPending(long timeoutMS) throws InterruptedException {
            long timeStart = SystemTimer.INSTANCE.milliTime();
            long timeLeft = timeoutMS;
            synchronized (numPending) {
                while ((numPending.get() > 0) && (timeLeft > 0)) {
                    numPending.wait(timeLeft);
                    timeLeft = timeoutMS - (SystemTimer.INSTANCE.milliTime() - timeStart);
                }
            }
            return numPending.get() <= 0;
        }

        void waitPending() throws InterruptedException {
            synchronized (numPending) {
                while (numPending.get() > 0) {
                    numPending.wait();
                }
            }
        }
    }

    public interface ResultCallback {

        /** Return true if we should continue waiting for replies for this sender. */
        boolean handleReply(Reply r);

        /**
         * Returns true if feeding has been aborted. No more feeding is allowed with this
         * callback after that.
         */
        boolean isAborted();

    }

}
