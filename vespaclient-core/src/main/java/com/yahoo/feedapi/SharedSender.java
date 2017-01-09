// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.*;
import com.yahoo.clientmetrics.RouteMetricSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
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
        OwnerState state = activeOwners.remove(owner);
        if (state != null) {
            state.clearPending();
        }
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
            OwnerState newState = new OwnerState();
            state = activeOwners.putIfAbsent(owner, newState);

        }
        if (state != null) {
            if (maxPendingPerOwner != -1 && blockingQueue) {
                state.waitMaxPendingBelow(maxPendingPerOwner);
            }
            state.addPending(1);
        }

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
        if (owner == null) {
            log.log(LogLevel.WARNING, "Received reply " + r + " for message " + r.getMessage() + " without context");
            return;
        }

        metrics.addReply(r);
        OwnerState state = activeOwners.get(owner);

        if (state == null) {
            // TODO: should be debug level if at all
            log.log(LogLevel.WARNING, "Owner " + owner.toString() + " is not active");
            return;
        }

        int numPending = state.getNumPending() - 1;
        boolean noMorePending = state.decPending(1);
        if (noMorePending) {
            numPending = 0;
        }
        boolean active = owner.handleReply(r, numPending);
        if (log.isLoggable(LogLevel.SPAM)) {
            log.log(LogLevel.SPAM, "Received reply for file " + owner.toString() + ", count was " + state.getNumPending());
        }
        if (!active) {
            state.clearPending();
            activeOwners.remove(owner);
        }
    }

    private static final class Sync extends AbstractQueuedSynchronizer {
        Sync(int initialCount) {
            setState(initialCount);
        }

        int getCount() {
            return getState();
        }

        @Override
        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }

        @Override
        protected boolean tryReleaseShared(int releases) {
            // Increment/Decrement count; signal when transition downwards to zero.
            // releases == 0 means unblock all
            while ( true ) {
                int c = getState();
                if ((c == 0) && (releases >= 0)) { return false; }
                int nextc = (c > releases) ? ((releases != 0) ? c - releases : 0) : 0;
                if (compareAndSetState(c, nextc)) {
                    return nextc == 0;
                }
            }
        }
    }

    private static final class OwnerState {

        private static final long REACT_LATENCY_ON_WATERMARK_MS = 5;

        private final Sync sync = new Sync(1);

        void addPending(int count) {
            sync.releaseShared(-count);
        }

        boolean decPending(int count) {
            return sync.releaseShared(count);
        }

        void waitMaxPendingBelow(int limit) {
            try {
                while (getNumPending() > limit) {
                    sync.tryAcquireSharedNanos(1, TimeUnit.MILLISECONDS.toNanos(REACT_LATENCY_ON_WATERMARK_MS));
                }
            } catch (InterruptedException e) {
            }
        }

        int getNumPending() {
            return sync.getCount();
        }

        void clearPending() {
            sync.releaseShared(0);
        }

        boolean waitPending(long timeoutMS) throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, TimeUnit.MILLISECONDS.toNanos(timeoutMS));
        }

        void waitPending() throws InterruptedException {
            sync.tryAcquireShared(1);
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
