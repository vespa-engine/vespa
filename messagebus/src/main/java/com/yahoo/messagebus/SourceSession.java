// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingTable;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A session supporting sending new messages.
 *
 * @author Simon Thoresen Hult
 */
public final class SourceSession implements ReplyHandler, MessageBus.SendBlockedMessages {

    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final CountDownLatch done = new CountDownLatch(1);
    private final AtomicBoolean sendingBlockedToken = new AtomicBoolean(false);
    private final Object lock = new Object();
    private final MessageBus mbus;
    private final Sequencer sequencer;
    private final ReplyHandler replyHandler;
    private final ThrottlePolicy throttlePolicy;
    private volatile double timeout;  // volatile only for tests
    private volatile int pendingCount = 0;
    private volatile boolean closed = false;
    private final Deque<BlockedMessage> blockedQ = new LinkedList<>();
    private final static class Counter {
        private int count = 0;
        void inc() { count ++; }
        void dec() { count --; }
        boolean enough() { return count > 5; }
    }
    private static ThreadLocal<Counter> sendBlockedRecurseLevel = ThreadLocal.withInitial(Counter::new);

    /**
     * The default constructor requires values for all final member variables
     * of this. It expects all arguments but the {@link SourceSessionParams} to
     * be proper, so no checks are performed. The constructor is declared
     * package private since only {@link MessageBus} is supposed to instantiate
     * it.
     *
     * @param mbus   the message bus that created this instance
     * @param params a parameter object that holds configuration parameters
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
        mbus.register(this);
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
     * @param message the message to send
     * @return the result of <i>initiating</i> sending of this message
     */
    public Result send(Message message) {
        return sendInternal(updateTiming(message));
    }

    private Message updateTiming(Message msg) {
        msg.setTimeReceivedNow();
        if (msg.getTimeRemaining() <= 0) {
            msg.setTimeRemaining((long)(timeout) * 1000L);
        }
        return msg;
    }

    private Result sendInternal(Message message) {
        synchronized (lock) {
            if (closed) {
                return new Result(ErrorCode.SEND_QUEUE_CLOSED, "Source session is closed.");
            }
            if (throttlePolicy != null && ! throttlePolicy.canSend(message, pendingCount)) {
                return new Result(ErrorCode.SEND_QUEUE_FULL,
                                  "Too much pending data (" + pendingCount + " messages).");
            }
            message.pushHandler(replyHandler);
            if (throttlePolicy != null) {
                throttlePolicy.processMessage(message);
            }
            ++pendingCount;
        }
        if (message.getTrace().shouldTrace(TraceLevel.COMPONENT)) {
            message.getTrace().trace(TraceLevel.COMPONENT,
                                     "Source session accepted a " + message.getApproxSize() + " byte message. " +
                                     pendingCount + " message(s) now pending.");
        }
        message.pushHandler(this);
        sequencer.handleMessage(message);
        return Result.ACCEPTED;
    }

    @Override
    public boolean trySend() {
        if (destroyed.get()) return false;
        sendBlockedMessages();
        expireStalledBlockedMessages();
        return true;
    }

    private class BlockedMessage {
        private final Message msg;
        private Result result = null;
        BlockedMessage(Message msg) {
            this.msg = msg;
        }

        private void notifyComplete(Result result) {
            synchronized (this) {
                this.result = result;
                notify();
            }
        }

        boolean notifyIfExpired() {
            if (msg.isExpired()) {
                Error error = new Error(ErrorCode.TIMEOUT, "Timed out in sendQ");
                notifyComplete(new Result(error));
                replyHandler.handleReply(createSendTimedOutReply(msg, error));
                return true;
            }
            return false;
        }

        boolean sendOrExpire() {
            if ( ! notifyIfExpired() ) {
                Result res = sendInternal(msg);
                if ( ! isSendQFull(res) ) {
                    notifyComplete(res);
                } else {
                    return false;
                }
            }
            return true;
        }

        Result waitComplete() throws InterruptedException {
            synchronized (this) {
                while (result == null) {
                    this.wait();
                }
            }
            return result;
        }
    }

    private Reply createSendTimedOutReply(Message msg, Error error) {
        Reply reply = new EmptyReply();
        reply.setMessage(msg);
        reply.addError(error);
        msg.swapState(reply);
        return reply;
    }

    static private boolean isSendQFull(Result res) {
        return !res.isAccepted() && (res.getError().getCode() == ErrorCode.SEND_QUEUE_FULL);
    }

    /**
     * This is a blocking proxy to the {@link #send(Message)} method. This
     * method blocks until the message is accepted by the send queue. Note that
     * the message timeout does not activate by calling this method. This method
     * will also return if this session is closed or the calling thread is
     * interrupted.
     *
     * @param msg the message to send
     * @return the result of initiating send
     * @throws InterruptedException thrown if the calling thread is interrupted
     */
    public Result sendBlocking(Message msg) throws InterruptedException {
        Result res = send(msg);
        if (isSendQFull(res)) {
            BlockedMessage blockedMessage = new BlockedMessage(msg);
            synchronized (blockedQ) {
                blockedQ.add(blockedMessage);
            }
            res = blockedMessage.waitComplete();
        }
        return res;
    }

    private void expireStalledBlockedMessages() {
        synchronized (blockedQ) {
            blockedQ.removeIf(BlockedMessage::notifyIfExpired);
        }
    }

    private BlockedMessage getNextBlockedMessage() {
        synchronized (blockedQ) {
            return blockedQ.poll();
        }
    }

    private void sendBlockedMessages() {
        Counter recurselevel = sendBlockedRecurseLevel.get();
        if (recurselevel.enough()) return;
        boolean someoneElseIsTakingCareOfIt = sendingBlockedToken.getAndSet(true);
        if (someoneElseIsTakingCareOfIt) return;
        try {
            recurselevel.inc();
            BlockedMessage msg = getNextBlockedMessage();
            for (boolean success = true; success && msg != null; ) {
                success = msg.sendOrExpire();
                if (!success) {
                    // Failed sending, put it back at the head of the Q.
                    synchronized (blockedQ) {
                        blockedQ.addFirst(msg);
                    }
                } else {
                    msg = getNextBlockedMessage();
                }
            }
        } finally {
            recurselevel.dec();
            sendingBlockedToken.set(false);
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
        }
        sendBlockedMessages();
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
     * This is a convenience function to assign a given route to the given
     * message, and then pass it to the other {@link #send(Message)} method of
     * this session.
     *
     * @param msg   the message to send
     * @param route the route to assign to the message
     * @return the immediate result of the attempt to send this message
     */
    public Result send(Message msg, Route route) {
        return send(msg.setRoute(route));
    }

    /**
     * This is a convenience method to call {@link
     * #send(Message,String,boolean)} with a <code>false</code> value for the
     * 'parseIfNotFound' parameter.
     *
     * @param msg       the message to send
     * @param routeName the route to assign to the message
     * @return the immediate result of the attempt to send this message
     */
    public Result send(Message msg, String routeName) {
        return send(msg, routeName, false);
    }

    /**
     * This is a convenience function to assign a named route to the given
     * message, and then pass it to the other {@link #send(Message)} method of
     * this session. If the route could not be found this methods returns with
     * an appropriate error, unless the 'parseIfNotFound' argument is true. In
     * that case, the route name is passed through to the Route factory method
     * {@link Route#parse}.
     *
     * @param msg             the message to send
     * @param routeName       the route to assign to the message
     * @param parseIfNotFound whether or not to parse routeName as a route if it could not be found
     * @return the immediate result of the attempt to send this message
     */
    public Result send(Message msg, String routeName, boolean parseIfNotFound) {
        boolean found = false;
        RoutingTable table = mbus.getRoutingTable(msg.getProtocol().toString());
        if (table != null) {
            Route route = table.getRoute(routeName);
            if (route != null) {
                msg.setRoute(new Route(route));
                found = true;
            } else if ( ! parseIfNotFound) {
                return new Result(ErrorCode.ILLEGAL_ROUTE,
                                  "Route '" + routeName + "' not found for protocol '" + msg.getProtocol() + "'.");
            }
        } else if ( ! parseIfNotFound) {
            return new Result(ErrorCode.ILLEGAL_ROUTE,
                              "Protocol '" + msg.getProtocol() + "' has no routing table.");
        }
        if ( ! found) {
            msg.setRoute(Route.parse(routeName));
        }
        return send(msg);
    }

    /** Returns the reply handler of this session */
    public ReplyHandler getReplyHandler() {
        return replyHandler;
    }

    /** Returns the number of messages sent that have not been replied to yet */
    public int getPendingCount() {
        return pendingCount;
    }

    /**
     * Sets the number of seconds a message can be attempted sent until it times out.
     *
     * @param timeout the number of seconds allowed.
     * @return this, to allow chaining.
     */
    public SourceSession setTimeout(double timeout) {
        this.timeout = timeout;
        return this;
    }

    public ThrottlePolicy getThrottlePolicy() {
        return throttlePolicy;
    }

}
