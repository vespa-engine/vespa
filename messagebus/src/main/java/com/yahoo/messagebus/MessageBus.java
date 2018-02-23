// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.concurrent.CopyOnWriteHashMap;
import com.yahoo.concurrent.SystemTimer;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.metrics.MessageBusMetricSet;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.network.NetworkOwner;
import com.yahoo.messagebus.routing.*;
import com.yahoo.text.Utf8Array;
import com.yahoo.text.Utf8String;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * <p>A message bus contains the factory for creating sessions to send, receive
 * and forward messages.</p>
 *
 * <p>There are three types of sessions:</p>
 * <ul>
 *     <li>{@link SourceSession Source sessions} sends messages and receives replies</li>
 *     <li>{@link IntermediateSession Intermediate sessions} receives messages on
 *         their way to their final destination, and may decide to forward the messages or reply directly.
 *     <li>{@link DestinationSession Destination sessions} are the final recipient
 *         of messages, and are expected to reply to every one of them, but may not forward messages.
 * </ul>
 *
 * <p>A message bus is configured with a {@link Protocol protocol}. This table
 * enumerates the permissible routes from intermediates to destinations and the
 * messaging semantics of each hop.</p>
 *
 * The responsibilities of a message bus are:
 * <ul>
 *     <li>Assign a route to every send message from its routing table
 *     <li>Deliver every message it <i>accepts</i> to the next hop on its route
 *         <i>or</i> deliver a <i>failure reply</i>.
 *     <li>Deliver replies back to message sources through all the intermediate hops.
 * </ul>
 *
 * A runtime will typically
 * <ul>
 *     <li>Create a message bus implementation and set properties on this implementation once.
 *     <li>Create sessions using that message bus many places.</li>
 * </ul>
 *
 * @author bratseth
 * @author Simon Thoresen
 */
public class MessageBus implements ConfigHandler, NetworkOwner, MessageHandler, ReplyHandler {

    private static Logger log = Logger.getLogger(MessageBus.class.getName());
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final ProtocolRepository protocolRepository = new ProtocolRepository();
    private final AtomicReference<Map<String, RoutingTable>> tablesRef = new AtomicReference<Map<String, RoutingTable>>(null);
    private final CopyOnWriteHashMap<String, MessageHandler> sessions = new CopyOnWriteHashMap<String, MessageHandler>();
    private final Network net;
    private final Messenger msn;
    private final Resender resender;
    private int maxPendingCount = 0;
    private int maxPendingSize = 0;
    private int pendingCount = 0;
    private int pendingSize = 0;
    private final Thread careTaker = new Thread(this::sendBlockedMessages);
    private final ConcurrentHashMap<SendBlockedMessages, Long> blockedSenders = new ConcurrentHashMap<>();
    private MessageBusMetricSet metrics = new MessageBusMetricSet();

    public interface SendBlockedMessages {
        /**
         * Do what you want, but dont block.
         * You will be called regularly until you signal you are done
         * @return true unless you are done
         */
        boolean trySend();
    }

    public void register(SendBlockedMessages sender) {
        blockedSenders.put(sender, SystemTimer.INSTANCE.milliTime());
    }

    private void sendBlockedMessages() {
        while (! destroyed.get()) {
            for (SendBlockedMessages sender : blockedSenders.keySet()) {
                if (!sender.trySend()) {
                    blockedSenders.remove(sender);
                }
            }
            try {

                Thread.sleep(10);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /**
     * <p>Convenience constructor that proxies {@link #MessageBus(Network,
     * MessageBusParams)} by adding the given protocols to a default {@link
     * MessageBusParams} object.</p>
     *
     * @param net       The network to associate with.
     * @param protocols An array of protocols to register.
     */
    public MessageBus(Network net, List<Protocol> protocols) {
        this(net, new MessageBusParams().addProtocols(protocols));
    }

    /**
     * <p>Constructs an instance of message bus. This requires a network object
     * that it will associate with. This assignment may not change during the
     * lifetime of this message bus.</p>
     *
     * @param net    The network to associate with.
     * @param params The parameters that controls this bus.
     */
    public MessageBus(Network net, MessageBusParams params) {
        // Add all known protocols to the repository.
        maxPendingCount = params.getMaxPendingCount();
        maxPendingSize  = params.getMaxPendingSize();
        for (int i = 0, len = params.getNumProtocols(); i < len; ++i) {
            protocolRepository.putProtocol(params.getProtocol(i));

            if (params.getProtocol(i).getMetrics() != null) {
                metrics.protocols.addMetric(params.getProtocol(i).getMetrics());
            }
        }

        // Attach and start network.
        this.net = net;
        net.attach(this);
        if ( ! net.waitUntilReady(120))
            throw new IllegalStateException("Network failed to become ready in time.");

        // Start messenger.
        msn = new Messenger();

        RetryPolicy retryPolicy = params.getRetryPolicy();
        if (retryPolicy != null) {
            resender = new Resender(retryPolicy);
            msn.addRecurrentTask(new ResenderTask(resender));
        } else {
            resender = null;
        }
        careTaker.setDaemon(true);
        careTaker.start();

        msn.start();
    }

    /**
     * <p>Returns the metrics used by this messagebus.</p>
     *
     * @return The metric set.
     */
    public MessageBusMetricSet getMetrics() {
        return metrics;
    }

    /**
     * <p>Sets the destroyed flag to true. The very first time this method is
     * called, it cleans up all its dependencies. Even if you retain a reference
     * to this object, all of its content is allowed to be garbage
     * collected.</p>
     *
     * @return True if content existed and was destroyed.
     */
    public boolean destroy() {
        if (!destroyed.getAndSet(true)) {
            try {
                careTaker.join();
            } catch (InterruptedException e) { }
            protocolRepository.clearPolicyCache();
            net.shutdown();
            msn.destroy();
            if (resender != null) {
                resender.destroy();
            }
            return true;
        }
        return false;
    }

    /**
     * <p>Synchronize with internal threads. This method will handshake with all
     * internal threads. This has the implicit effect of waiting for all active
     * callbacks. Note that this method should never be invoked from a callback
     * since that would make the thread wait for itself... forever. This method
     * is typically used to untangle during session shutdown.</p>
     */
    public void sync() {
        msn.sync();
        net.sync();
    }

    /**
     * <p>This is a convenience method to call {@link
     * #createSourceSession(SourceSessionParams)} with default values for the
     * {@link SourceSessionParams} object.</p>
     *
     * @param handler The reply handler to receive the replies for the session.
     * @return The created session.
     */
    public SourceSession createSourceSession(ReplyHandler handler) {
        return createSourceSession(new SourceSessionParams().setReplyHandler(handler));
    }

    /**
     * <p>This is a convenience method to call {@link
     * #createSourceSession(SourceSessionParams)} by first assigning the reply
     * handler to the parameter object.</p>
     *
     * @param handler The reply handler to receive the replies for the session.
     * @param params  The parameters to control the session.
     * @return The created session.
     */
    public SourceSession createSourceSession(ReplyHandler handler, SourceSessionParams params) {
        return createSourceSession(new SourceSessionParams(params).setReplyHandler(handler));
    }

    /**
     * <p>Creates a source session on top of this message bus.</p>
     *
     * @param params The parameters to control the session.
     * @return The created session.
     */
    public SourceSession createSourceSession(SourceSessionParams params) {
        if (destroyed.get()) {
            throw new IllegalStateException("Object is destroyed.");
        }
        return new SourceSession(this, params);
    }

    /**
     * <p>This is a convenience method to call {@link
     * #createIntermediateSession(IntermediateSessionParams)} with default
     * values for the {@link IntermediateSessionParams} object.</p>
     *
     * @param name          The local unique name for the created session.
     * @param broadcastName Whether or not to broadcast this session's name on
     *                      the network.
     * @param msgHandler    The handler to receive the messages for the session.
     * @param replyHandler  The handler to received the replies for the session.
     * @return The created session.
     */
    public IntermediateSession createIntermediateSession(String name,
                                                         boolean broadcastName,
                                                         MessageHandler msgHandler,
                                                         ReplyHandler replyHandler) {
        return createIntermediateSession(
                new IntermediateSessionParams()
                        .setName(name)
                        .setBroadcastName(broadcastName)
                        .setMessageHandler(msgHandler)
                        .setReplyHandler(replyHandler));
    }

    /**
     * <p>Creates an intermediate session on top of this message bus using the
     * given handlers and parameter object.</p>
     *
     * @param params The parameters to control the session.
     * @return The created session.
     */
    public synchronized IntermediateSession createIntermediateSession(IntermediateSessionParams params) {
        if (destroyed.get()) {
            throw new IllegalStateException("Object is destroyed.");
        }
        if (sessions.containsKey(params.getName())) {
            throw new IllegalArgumentException("Name '" + params.getName() + "' is not unique.");
        }
        IntermediateSession session = new IntermediateSession(this, params);
        sessions.put(params.getName(), session);
        if (params.getBroadcastName()) {
            net.registerSession(params.getName());
        }
        return session;
    }

    /**
     * <p>This is a convenience method to call {@link
     * #createDestinationSession(DestinationSessionParams)} with default values
     * for the {@link DestinationSessionParams} object.</p>
     *
     * @param name          The local unique name for the created session.
     * @param broadcastName Whether or not to broadcast this session's name on
     *                      the network.
     * @param handler       The handler to receive the messages for the session.
     * @return The created session.
     */
    public DestinationSession createDestinationSession(String name,
                                                       boolean broadcastName,
                                                       MessageHandler handler) {
        return createDestinationSession(
                new DestinationSessionParams()
                        .setName(name)
                        .setBroadcastName(broadcastName)
                        .setMessageHandler(handler));
    }

    /**
     * <p>Creates a destination session on top of this message bus using the
     * given handlers and parameter object.</p>
     *
     * @param params The parameters to control the session.
     * @return The created session.
     */
    public synchronized DestinationSession createDestinationSession(DestinationSessionParams params) {
        if (destroyed.get()) {
            throw new IllegalStateException("Object is destroyed.");
        }
        if (sessions.containsKey(params.getName())) {
            throw new IllegalArgumentException("Name '" + params.getName() + "' is not unique.");
        }
        DestinationSession session = new DestinationSession(this, params);
        sessions.put(params.getName(), session);
        if (params.getBroadcastName()) {
            net.registerSession(params.getName());
        }
        return session;
    }

    /**
     * <p>This method is invoked by the {@link
     * com.yahoo.messagebus.IntermediateSession#destroy()} to unregister
     * sessions from receiving data from message bus.</p>
     *
     * @param name          The name of the session to remove.
     * @param broadcastName Whether or not session name was broadcast.
     */
    public synchronized void unregisterSession(String name, boolean broadcastName) {
        if (broadcastName) {
            net.unregisterSession(name);
        }
        sessions.remove(name);
    }

    private boolean doAccounting() {
        return (maxPendingCount > 0 || maxPendingSize > 0);
    }
    /**
     * <p>This method handles choking input data so that message bus does not
     * blindly accept everything. This prevents an application running
     * out-of-memory in case it fail to choke input data itself. If this method
     * returns false, it means that it should be rejected.</p>
     *
     * @param msg The message to count.
     * @return True if the message was accepted.
     */
    private boolean checkPending(Message msg) {
        boolean busy = false;
        int size = msg.getApproxSize();

        if (doAccounting()) {
            synchronized (this) {
                busy = ((maxPendingCount > 0 && pendingCount >= maxPendingCount) ||
                        (maxPendingSize > 0 && pendingSize >= maxPendingSize));
                if (!busy) {
                    pendingCount++;
                    pendingSize += size;
                }
            }
        }
        if (busy) {
            return false;
        }
        msg.setContext(size);
        msg.pushHandler(this);
        return true;
    }

    @Override
    public void handleMessage(Message msg) {
        if (resender != null && msg.hasBucketSequence()) {
            deliverError(msg, ErrorCode.SEQUENCE_ERROR, "Bucket sequences not supported when resender is enabled.");
            return;
        }
        SendProxy proxy = new SendProxy(this, net, resender);
        msn.deliverMessage(msg, proxy);
    }

    @Override
    public void handleReply(Reply reply) {
        if (destroyed.get()) {
            reply.discard();
            return;
        }
        if (doAccounting()) {
            synchronized (this) {
                --pendingCount;
                pendingSize -= (Integer)reply.getContext();
            }
        }
        deliverReply(reply, reply.popHandler());
    }

    @Override
    public void deliverMessage(Message msg, String session) {
        MessageHandler msgHandler = sessions.get(session);
        if (msgHandler == null) {
            deliverError(msg, ErrorCode.UNKNOWN_SESSION, "Session '" + session + "' does not exist.");
        } else if (!checkPending(msg)) {
            deliverError(msg, ErrorCode.SESSION_BUSY, "Session '" + net.getConnectionSpec() + "/" + session +
                                                      "' is busy, try again later.");
        } else {
            msn.deliverMessage(msg, msgHandler);
        }
    }

    /**
     * <p>Adds a protocol to the internal repository of protocols, replacing any
     * previous instance of the protocol and clearing the associated routing
     * policy cache.</p>
     *
     * @param protocol The protocol to add.
     */
    public void putProtocol(Protocol protocol) {
        protocolRepository.putProtocol(protocol);
    }

    @Override
    public Protocol getProtocol(Utf8Array name) {
        return protocolRepository.getProtocol(name.toString());
    }

    public Protocol getProtocol(Utf8String name) {
        return getProtocol((Utf8Array)name);
    }

    @Override
    public void deliverReply(Reply reply, ReplyHandler handler) {
        msn.deliverReply(reply, handler);
    }

    @Override
    public void setupRouting(RoutingSpec spec) {
        Map<String, RoutingTable> tables = new HashMap<String, RoutingTable>();
        for (int i = 0, len = spec.getNumTables(); i < len; ++i) {
            RoutingTableSpec table = spec.getTable(i);
            String name = table.getProtocol();
            if (!protocolRepository.hasProtocol(name)) {
                log.log(LogLevel.INFO, "Protocol '" + name + "' is not supported, ignoring routing table.");
                continue;
            }
            tables.put(name, new RoutingTable(table));
        }
        tablesRef.set(tables);
        protocolRepository.clearPolicyCache();
    }

    /**
     * <p>Returns the resender that is running within this message bus.</p>
     *
     * @return The resender.
     */
    public Resender getResender() {
        return resender;
    }

    /**
     * <p>Returns the number of messages received that have not been replied to
     * yet.</p>
     *
     * @return The pending count.
     */
    public synchronized int getPendingCount() {
        return pendingCount;
    }

    /**
     * <p>Returns the size of messages received that have not been replied to
     * yet.</p>
     *
     * @return The pending size.
     */
    public synchronized int getPendingSize() {
        return pendingSize;
    }

    /**
     * <p>Sets the maximum number of messages that can be received without being
     * replied to yet.</p>
     *
     * @param maxCount The max count.
     */
    public void setMaxPendingCount(int maxCount) {
        maxPendingCount = maxCount;
    }

    /**
     * Gets maximum number of messages that can be received without being
     * replied to yet.
     */
    public int getMaxPendingCount() {
        return maxPendingCount;
    }

    /**
     * <p>Sets the maximum size of messages that can be received without being
     * replied to yet.</p>
     *
     * @param maxSize The max size.
     */
    public void setMaxPendingSize(int maxSize) {
        maxPendingSize = maxSize;
    }

    /**
     * Gets maximum combined size of messages that can be received without
     * being replied to yet.
     */
    public int getMaxPendingSize() {
        return maxPendingSize;
    }

    /**
     * <p>Returns a named routing table, may return null.</p>
     *
     * @param name The name of the routing table to return.
     * @return The routing table object.
     */
    public RoutingTable getRoutingTable(String name) {
        Map<String, RoutingTable> tables = tablesRef.get();
        if (tables == null) {
            return null;
        }
        return tables.get(name);
    }
    /**
     * <p>Returns a named routing table, may return null.</p>
     *
     * @param name The name of the routing table to return.
     * @return The routing table object.
     */
    public RoutingTable getRoutingTable(Utf8String name) {

        return getRoutingTable(name.toString());
    }

    /**
     * <p>Returns a routing policy that corresponds to the argument protocol
     * name, policy name and policy parameter. This will cache reuse all
     * policies as soon as they are first requested.</p>
     *
     * @param protocolName The name of the protocol to invoke {@link Protocol#createPolicy(String,String)} on.
     * @param policyName   The name of the routing policy to retrieve.
     * @param policyParam  The parameter for the routing policy to retrieve.
     * @return A corresponding routing policy, or null.
     */
    public RoutingPolicy getRoutingPolicy(String protocolName, String policyName, String policyParam) {
        return protocolRepository.getRoutingPolicy(protocolName, policyName, policyParam);
    }

    /**
     * <p>Returns a routing policy that corresponds to the argument protocol
     * name, policy name and policy parameter. This will cache reuse all
     * policies as soon as they are first requested.</p>
     *
     * @param protocolName The name of the protocol to invoke {@link Protocol#createPolicy(String,String)} on.
     * @param policyName   The name of the routing policy to retrieve.
     * @param policyParam  The parameter for the routing policy to retrieve.
     * @return A corresponding routing policy, or null.
     */
    public RoutingPolicy getRoutingPolicy(Utf8String protocolName, String policyName, String policyParam) {
        return protocolRepository.getRoutingPolicy(protocolName.toString(), policyName, policyParam);
    }

    /**
     * <p>Returns the connection spec string for the network layer of this
     * message bus. This is merely a proxy of the same function in the network
     * layer.</p>
     *
     * @return The connection string.
     */
    public String getConnectionSpec() {
        return net.getConnectionSpec();
    }

    /**
     * <p>Constructs and schedules a Reply containing an error to the handler of the given Message.</p>
     *
     * @param msg     The message to reply to.
     * @param errCode The code of the error to set.
     * @param errMsg  The message of the error to set.
     */
    private void deliverError(Message msg, int errCode, String errMsg) {
        Reply reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(errCode, errMsg));
        deliverReply(reply, reply.popHandler());
    }

    /**
     * <p>Implements a task for running the resender in the messenger
     * thread. This task acts as a proxy for the resender, allowing the task to
     * be deleted without affecting the resender itself.</p>
     */
    private static class ResenderTask implements Messenger.Task {

        final Resender resender;

        ResenderTask(Resender resender) {
            this.resender = resender;
        }

        public void destroy() {
            // empty
        }

        public void run() {
            resender.resendScheduled();
        }

    }
}

