// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingTable;
import com.yahoo.text.Utf8String;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static java.lang.System.nanoTime;

/**
 * <p>A session supporting sending new messages.</p>
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public final class SourceSession implements ReplyHandler {

    private static Logger log = Logger.getLogger(SourceSession.class.getName());
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final CountDownLatch done = new CountDownLatch(1);
    private final Object lock = new Object();
    private final MessageBus mbus;
    private final Sequencer sequencer;
    private final ReplyHandler replyHandler;
    private final ThrottlePolicy throttlePolicy;
    private volatile double timeout;
    private volatile int pendingCount = 0;
    private boolean closed = false;
    private final Queue<BlockingHandler> blockedQ = new LinkedList<>();

    /**
     * <p>The default constructor requires values for all final member variables
     * of this. It expects all arguments but the {@link SourceSessionParams} to
     * be proper, so no checks are performed. The constructor is declared
     * package private since only {@link MessageBus} is supposed to instantiate
     * it.</p>
     *
     * @param mbus   The message bus that created this instance.
     * @param params A parameter object that holds configuration parameters.
     */
    SourceSession(MessageBus mbus, SourceSessionParams params) {
        this.mbus = mbus;
        sequencer = new Sequencer(mbus);
        if (!params.hasReplyHandler()) {
             throw new NullPointerException("Reply handler is null.");
        }
        replyHandler = params.getReplyHandler();
        throttlePolicy = params.getThrottlePolicy();
        timeout = params.getTimeout();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (destroy()) {
                log.log(LogLevel.WARNING, "SourceSession destroyed by finalizer, please review application shutdown logic.");
            }
        } finally {
            super.finalize();
        }
    }

    /**
     * Sets the destroyed flag to true. The very first time this method is
     * called, it cleans up all its dependencies.  Even if you retain a
     * reference to this object, all of its content is allowed to be garbage
     * collected.
     *
     * @return true if content existed and was destroyed.
     */
    public boolean destroy() {
        if (destroyed.getAndSet(true)) {
            return false;
        }
        synchronized (lock) {
            closed = true;
        }
        sequencer.destroy();
        mbus.sync();
        return true;
    }

    /**
     * Reject all new messages and wait until no messages are pending. Before
     * returning, this method calls {@link #destroy()}.
     */
    public void close() {
        synchronized (lock) {
            closed = true;
        }
        if (pendingCount == 0) {
            done.countDown();
        }
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        destroy();
    }

    /**
     * <p>Sends a new message. Calling this immediately causes one of three
     * possible results:</p>
     * <ul><li>A result is returned indicating that the message is accepted. In
     * this case, a reply to the message is guaranteed to be produced on this
     * session within a timeout limit. That reply may indicate either success or
     * failure.</li>
     * <li>A result is returned indicating that the message is not
     * accepted. This is a <i>transient failure</i>, retrying the same operation
     * after some wait period should cause it to be accepted.</li>
     * <li>An exception is thrown, indicating a non-transient error which is not
     * expected to be fixed before some corrective action is taken.</li> </ul>
     *
     * <p>A source client should typically do some equivalent of:</p>
     * <code>
     * do {
     *     Result result = sourceSession.send(message);
     *     if (!result.isAccepted())
     *         // Do something else or wait a while
     * } while (!result.isAccepted());
     * </code>
     *
     * @param msg the message to send
     * @return The result of <i>initiating</i> sending of this message.
     */
    public Result send(Message msg) {
        msg.setTimeReceivedNow();
        if (msg.getTimeRemaining() <= 0) {
            msg.setTimeRemaining((long)(timeout * 1000));
        }
        synchronized (lock) {
            if (closed) {
                return new Result(ErrorCode.SEND_QUEUE_CLOSED,
                                  "Source session is closed.");
            }
            if (throttlePolicy != null && !throttlePolicy.canSend(msg, pendingCount)) {
                return new Result(ErrorCode.SEND_QUEUE_FULL,
                                  "Too much pending data (" + pendingCount + " messages).");
            }
            msg.pushHandler(replyHandler);
            if (throttlePolicy != null) {
                throttlePolicy.processMessage(msg);
            }
            ++pendingCount;
        }
        if (msg.getTrace().shouldTrace(TraceLevel.COMPONENT)) {
            msg.getTrace().trace(TraceLevel.COMPONENT,
                                 "Source session accepted a " + msg.getApproxSize() + " byte message. " +
                                 pendingCount + " message(s) now pending.");
        }
        msg.pushHandler(this);
        sequencer.handleMessage(msg);
        return Result.ACCEPTED;
    }

    static private class BlockingHandler {

        private boolean complete = false;
        private final Message msg;
        BlockingHandler(Message msg) {
            this.msg = msg;
        }

        void sendComplete() {
            synchronized (this) {
                complete = true;
                notify();
            }
        }

        Message getMessage() { return msg; }
        boolean waitComplete() throws InterruptedException {

            synchronized (this) {

                while( !complete && !msg.isExpired() ) {
                    wait(msg.getTimeRemainingNow());
                }
                return complete;
            }
        }
    }

    static public class SendTimeoutReply extends Reply {

        SendTimeoutReply(Message msg) {
            setMessage(msg);
            addError(new Error(ErrorCode.TIMEOUT, "Timed out in sendQ"));
        }

        @Override
        public Utf8String getProtocol() {
            return null;
        }

        @Override
        public int getType() {
            return 0;
        }
    }

    /**
     * <p>This is a blocking proxy to the {@link #send(Message)} method. This
     * method blocks until the message is accepted by the send queue. Note that
     * the message timeout does not activate by calling this method. This method
     * will also return if this session is closed or the calling thread is
     * interrupted.</p>
     *
     * @param msg The message to send.
     * @return The result of initiating send.
     * @throws InterruptedException Thrown if the calling thread is interrupted.
     */
    public Result sendBlocking(Message msg) throws InterruptedException {
        while (true) {
            Result res = send(msg);
            if (res.isAccepted() || res.getError().getCode() != ErrorCode.SEND_QUEUE_FULL) {
                return res;
            }
            BlockingHandler sendHandler = null;
            synchronized (lock) {
                if (!closed && !throttlePolicy.canSend(msg, pendingCount)) {
                    sendHandler = new BlockingHandler(msg);
                    blockedQ.add(sendHandler);
                }
            }
            if ( (sendHandler != null) && !sendHandler.waitComplete() ) {
                replyHandler.handleReply(new SendTimeoutReply(msg));
            }
        }
    }

    private void drainQ() {
        synchronized (lock) {
            while (!blockedQ.isEmpty() ) {
                BlockingHandler handler = blockedQ.element();
                if ( ! handler.getMessage().isExpired() ) {
                    if (throttlePolicy.canSend(handler.getMessage(), pendingCount)) {
                        handler.sendComplete();
                    } else {
                        return;
                    }
                }
                blockedQ.remove();
            }
        }
    }

    @Override
    public void handleReply(Reply reply) {
        if (destroyed.get()) {
            reply.discard();
            return;
        }
        boolean done;
        synchronized (lock) {
            --pendingCount;
            if (throttlePolicy != null) {
                throttlePolicy.processReply(reply);
            }
            done = (closed && pendingCount == 0);
            drainQ();
        }
        if (reply.getTrace().shouldTrace(TraceLevel.COMPONENT)) {
            reply.getTrace().trace(TraceLevel.COMPONENT,
                                   "Source session received reply. " + pendingCount + " message(s) now pending.");
        }
        ReplyHandler handler = reply.popHandler();
        handler.handleReply(reply);
        if (done) {
            this.done.countDown();
        }
    }

    /**
     * <p>This is a convenience function to assign a given route to the given
     * message, and then pass it to the other {@link #send(Message)} method of
     * this session.</p>
     *
     * @param msg   The message to send.
     * @param route The route to assign to the message.
     * @return The immediate result of the attempt to send this message.
     */
    public Result send(Message msg, Route route) {
        return send(msg.setRoute(route));
    }

    /**
     * <p>This is a convenience method to call {@link
     * #send(Message,String,boolean)} with a <code>false</code> value for the
     * 'parseIfNotFound' parameter.</p>
     *
     * @param msg       The message to send.
     * @param routeName The route to assign to the message.
     * @return The immediate result of the attempt to send this message.
     */
    public Result send(Message msg, String routeName) {
        return send(msg, routeName, false);
    }

    /**
     * <p>This is a convenience function to assign a named route to the given
     * message, and then pass it to the other {@link #send(Message)} method of
     * this session. If the route could not be found this methods returns with
     * an appropriate error, unless the 'parseIfNotFound' argument is true. In
     * that case, the route name is passed through to the Route factory method
     * {@link Route#parse}.</p>
     *
     * @param msg             The message to send.
     * @param routeName       The route to assign to the message.
     * @param parseIfNotFound Whether or not to parse routeName as a route if
     *                        it could not be found.
     * @return The immediate result of the attempt to send this message.
     */
    public Result send(Message msg, String routeName, boolean parseIfNotFound) {
        boolean found = false;
        RoutingTable table = mbus.getRoutingTable(msg.getProtocol().toString());
        if (table != null) {
            Route route = table.getRoute(routeName);
            if (route != null) {
                msg.setRoute(new Route(route));
                found = true;
            } else if (!parseIfNotFound) {
                return new Result(ErrorCode.ILLEGAL_ROUTE,
                                  "Route '" + routeName + "' not found for protocol '" + msg.getProtocol() + "'.");
            }
        } else if (!parseIfNotFound) {
            return new Result(ErrorCode.ILLEGAL_ROUTE,
                              "Protocol '" + msg.getProtocol() + "' has no routing table.");
        }
        if (!found) {
            msg.setRoute(Route.parse(routeName));
        }
        return send(msg);
    }

    /**
     * <p>Returns the reply handler of this session.</p>
     *
     * @return The reply handler.
     */
    public ReplyHandler getReplyHandler() {
        return replyHandler;
    }

    /**
     * <p>Returns the number of messages sent that have not been replied to
     * yet.</p>
     *
     * @return The pending count.
     */
    public int getPendingCount() {
        return pendingCount;
    }

    /**
     * <p>Sets the number of seconds a message can be attempted sent until it
     * times out.</p>
     *
     * @param timeout The numer of seconds allowed.
     * @return This, to allow chaining.
     */
    public SourceSession setTimeout(double timeout) {
        this.timeout = timeout;
        return this;
    }
}
