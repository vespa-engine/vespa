// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.concurrent.SystemTimer;
import java.util.logging.Level;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.clientmetrics.RouteMetricSet;

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

    private final SendSession sender;
    private final RouteMetricSet metrics;
    private final Pending globalPending = new Pending();

    /**
     * Creates a new shared sender.
     * If oldsender != null, we copy that status information from that sender.
     */
    SharedSender(String route, SessionFactory factory, SharedSender oldSender) {
        sender = (factory != null) ? factory.createSendSession(this) : null;
        metrics = (oldSender != null) ? oldSender.metrics : new RouteMetricSet(route, null);
    }

    public RouteMetricSet getMetrics() {
        return metrics;
    }

    public void shutdown() {
        try {
            globalPending.waitForZero();
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
    boolean waitForPending(ResultCallback owner, long timeoutMs) {
        try {
            return owner.getPending().waitForZero(timeoutMs);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Sends a message
     *
     * @param msg   The message to send.
     * @param owner A callback to send replies to when received from messagebus
     */
    public void send(Message msg, ResultCallback owner) {
        send(msg, owner, true);
    }

    /**
     * Sends a message. Waits until the number of pending messages for this owner has
     * become lower than the specified limit if necessary.
     *
     * @param msg                The message to send
     * @param owner              The callback to send replies to when received from messagebus
     * @param blockingQueue      If true, block until the message bus queue is available.
     */
    public void send(Message msg, ResultCallback owner, boolean blockingQueue) {
        // Silently fail messages that are attempted sent after the callback aborted.
        if (owner.isAborted()) {
            return;
        }

        msg.setContext(owner);
        owner.getPending().inc();
        globalPending.inc();

        com.yahoo.messagebus.Result r;
        try {
            r = sender.send(msg, blockingQueue);
        } catch (InterruptedException e) {
            r = null;
        }
        if (r == null || !r.isAccepted()) {
            // pretend we sent OK but got this error reply:
            EmptyReply reply = new EmptyReply();
            msg.swapState(reply);
            reply.setMessage(msg);
            if (r != null) {
                reply.addError(r.getError());
            }
            handleReply(reply);
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
        globalPending.dec();
        ResultCallback owner = (ResultCallback) r.getContext();

        if (owner != null) {
            synchronized (metrics) {
                metrics.addReply(r);
            }
            log.log(Level.FINEST, () -> "Received reply for file " + owner.toString() + " count was " + owner.getPending().val());
            if (owner.isAborted()) {
                log.log(Level.FINE, () -> "Received reply for file " + owner.toString() + " which is aborted");
                owner.getPending().clear();
                return;
            }
            if (owner.handleReply(r)) {
                owner.getPending().dec();
            } else {
                log.log(Level.FINE, () -> "Received reply for file " + owner.toString() + " which wants to abort");
                owner.getPending().clear();
            }
        } else {
            log.log(Level.WARNING, "Received reply " + r + " for message " + r.getMessage() + " without context");
        }
    }

    public static class Pending {
        private int value = 0;
        public synchronized void inc() { ++value; }
        synchronized void dec() { if (--value == 0) notifyAll(); }
        public synchronized void clear() { value = 0; notifyAll(); }
        public synchronized int val() { return value; }
        synchronized boolean waitForZero() throws InterruptedException {
            while (value > 0) {
                wait();
            }
            return true;
        }
        boolean waitForZero(long timeoutMs) throws InterruptedException {
            if (timeoutMs == -1) {
                return waitForZero();
            } else {
                long timeStart = SystemTimer.INSTANCE.milliTime();
                long timeLeft = timeoutMs;
                while (timeLeft > 0) {
                    synchronized(this) {
                        if (value > 0) {
                            wait(timeLeft);
                        } else {
                            return true;
                        }
                    }
                    long elapsed = SystemTimer.INSTANCE.milliTime() - timeStart;
                    timeLeft = timeoutMs - elapsed;
                }
                return false;
            }
        }
    }

    public interface ResultCallback {

        /** get the associated Pending number */
        Pending getPending();

        /** Return true unless we should abort this sender. */
        boolean handleReply(Reply r);

        /**
         * Returns true if feeding has been aborted. No more feeding is allowed with this
         * callback after that.
         */
        boolean isAborted();
    }

}
