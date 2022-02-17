// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.TraceLevel;

import java.util.PriorityQueue;
import java.util.LinkedList;
import java.util.List;

/**
 * The resender handles scheduling and execution of sending instances of {@link RoutingNode}. An instance of this class
 * is owned by {@link com.yahoo.messagebus.MessageBus}. Because this class does not have any internal thread, it depends
 * on message bus to keep polling it whenever it has time.
 *
 * @author Simon Thoresen Hult
 */
public class Resender {

    private final Object monitor = new Object();
    private final PriorityQueue<Entry> queue = new PriorityQueue<>();
    private final RetryPolicy retryPolicy;
    private boolean destroyed = false;

    /**
     * Constructs a new resender.
     *
     * @param retryPolicy The retry policy to use.
     */
    public Resender(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    /**
     * Returns whether or not the current {@link RetryPolicy} supports resending a {@link Reply} that contains an error
     * with the given error code.
     *
     * @param errorCode The code to check.
     * @return True if the message can be resent.
     */
    public boolean canRetry(int errorCode) {
        return retryPolicy.canRetry(errorCode);
    }

    /**
     * Returns whether or not the given reply should be retried.
     *
     * @param reply The reply to check.
     * @return True if retry is required.
     */
    boolean shouldRetry(Reply reply) {
        int numErrors = reply.getNumErrors();
        if (numErrors == 0) {
            return false;
        }
        for (int i = 0; i < numErrors; ++i) {
            if (!retryPolicy.canRetry(reply.getError(i).getCode())) {
                return false;
            }
        }
        synchronized (monitor) {
            return !destroyed;
        }
    }

    /**
     * Schedules the given node for resending, if enabled. This will invoke {@link com.yahoo.messagebus.routing.RoutingNode#prepareForRetry()}
     * if the node was queued. This method is NOT thread-safe, and should only be called by the messenger thread.
     *
     * @param node  The node to resend.
     * @return True if the node was queued.
     */
    boolean scheduleRetry(RoutingNode node) {
        Message msg = node.getMessage();
        if (!msg.getRetryEnabled()) {
            return false;
        }
        int retry = msg.getRetry() + 1;
        double delay = node.getReply().getRetryDelay();
        if (delay < 0) {
            delay = retryPolicy.getRetryDelay(retry);
        }
        if (msg.getTimeRemainingNow() * 0.001 - delay <= 0) {
            node.addError(ErrorCode.TIMEOUT, "Timeout exceeded by resender, giving up.");
            return false;
        }
        synchronized (monitor) {
            if (destroyed) return false;
            node.prepareForRetry(); // consumes the reply
            node.getTrace().trace(TraceLevel.COMPONENT,
                    "Message scheduled for retry " + retry + " in " + delay + " seconds.");
            msg.setRetry(retry);
            queue.add(new Entry(node, SystemTimer.INSTANCE.milliTime() + (long) (delay * 1000)));
        }
        return true;
    }

    /**
     * Invokes {@link RoutingNode#send()} on all routing nodes that are applicable for sending at the current time.
     */
    public void resendScheduled() {
        List<RoutingNode> sendList;

        long now = SystemTimer.INSTANCE.milliTime();
        synchronized (monitor) {
            if (queue.isEmpty()) return;
            sendList = new LinkedList<>();
            while (!queue.isEmpty() && queue.peek().time <= now) {
                sendList.add(queue.poll().node);
            }
        }

        for (RoutingNode node : sendList) {
            node.getTrace().trace(TraceLevel.COMPONENT, "Resender resending message.");
            node.send();
        }
    }

    /**
     * Discards all the routing nodes currently scheduled for resending.
     */
    public void destroy() {
        synchronized (monitor) {
            while (!queue.isEmpty()) {
                queue.poll().node.discard();
            }
            destroyed = true;
        }
    }

    /**
     * This class encapsulates a routing node and some arbitrary time. This is required for the resending logic so that
     * it can properly schedule resending.
     */
    private static class Entry implements Comparable<Entry> {

        final RoutingNode node;
        final Long time;

        /**
         * The default constructor requires initial values for both members.
         *
         * @param node The routing node being scheduled.
         * @param time The time of this schedule.
         */
        Entry(RoutingNode node, long time) {
            this.node = node;
            this.time = time;
        }

        @Override
        public int compareTo(Entry rhs) {
            return time.compareTo(rhs.time);
        }
    }
}
