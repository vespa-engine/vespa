// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.local;

import com.yahoo.component.Vtag;
import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Routable;
import com.yahoo.messagebus.TraceNode;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.network.NetworkOwner;
import com.yahoo.messagebus.network.ServiceAddress;
import com.yahoo.messagebus.routing.RoutingNode;
import com.yahoo.text.Utf8String;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.yahoo.messagebus.ErrorCode.NO_ADDRESS_FOR_SERVICE;

/**
 * @author Simon Thoresen Hult
 */
public class LocalNetwork implements Network {

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final LocalWire wire;
    private final String hostId;
    private volatile NetworkOwner owner;

    public LocalNetwork() {
        this(new LocalWire());
    }

    public LocalNetwork(LocalWire wire) {
        this.wire = wire;
        this.hostId = wire.newHostId();
    }

    @Override
    public boolean waitUntilReady(double seconds) {
        return true;
    }

    @Override
    public void attach(NetworkOwner owner) {
        this.owner = owner;
    }

    @Override
    public void registerSession(String session) {
        wire.registerService(hostId + "/" + session, this);
    }

    @Override
    public void unregisterSession(String session) {
        wire.unregisterService(hostId + "/" + session);
    }

    @Override
    public boolean allocServiceAddress(RoutingNode recipient) {
        String service = recipient.getRoute().getHop(0).getServiceName();
        ServiceAddress address = wire.resolveServiceAddress(service);
        if (address == null) {
            recipient.setError(new Error(NO_ADDRESS_FOR_SERVICE, "No address for service '" + service + "'."));
            return false;
        }
        recipient.setServiceAddress(address);
        return true;
    }

    @Override
    public void freeServiceAddress(RoutingNode recipient) {
        recipient.setServiceAddress(null);
    }

    @Override
    public void send(Message msg, List<RoutingNode> recipients) {
        for (RoutingNode recipient : recipients) {
            new MessageEnvelope(this, msg, recipient).send();
        }
    }

    private void receiveLater(MessageEnvelope envelope) {
        byte[] payload = envelope.sender.encode(envelope.msg.getProtocol(), envelope.msg);
        executor.execute(new Runnable() {

            @Override
            public void run() {
                Message msg = decode(envelope.msg.getProtocol(), payload, Message.class);
                msg.getTrace().setLevel(envelope.msg.getTrace().getLevel());
                msg.setRoute(envelope.msg.getRoute()).getRoute().removeHop(0);
                msg.setRetryEnabled(envelope.msg.getRetryEnabled());
                msg.setRetry(envelope.msg.getRetry());
                msg.setTimeRemaining(envelope.msg.getTimeRemainingNow());
                msg.pushHandler(reply -> new ReplyEnvelope(LocalNetwork.this, envelope, reply).send());
                owner.deliverMessage(msg, ((LocalServiceAddress) envelope.recipient.getServiceAddress()).getSessionName());
            }
        });
    }

    private void receiveLater(ReplyEnvelope envelope) {
        byte[] payload = envelope.sender.encode(envelope.reply.getProtocol(), envelope.reply);
        executor.execute(() -> {
            Reply reply = decode(envelope.reply.getProtocol(), payload, Reply.class);
            reply.setRetryDelay(envelope.reply.getRetryDelay());
            reply.getTrace().getRoot().addChild(TraceNode.decode(envelope.reply.getTrace().getRoot().encode()));
            for (int i = 0, len = envelope.reply.getNumErrors(); i < len; ++i) {
                Error error = envelope.reply.getError(i);
                reply.addError(new Error(error.getCode(),
                                         error.getMessage(),
                                         error.getService() != null ? error.getService() : envelope.sender.hostId));
            }
            owner.deliverReply(reply, envelope.parent.recipient);
        });
    }

    private byte[] encode(Utf8String protocolName, Routable toEncode) {
        if (toEncode.getType() == 0) {
            return new byte[0];
        }
        return owner.getProtocol(protocolName).encode(Vtag.currentVersion, toEncode);
    }

    private <T extends Routable> T decode(Utf8String protocolName, byte[] toDecode, Class<T> clazz) {
        return clazz.cast(toDecode.length == 0 ? new EmptyReply()
                                               : owner.getProtocol(protocolName).decode(Vtag.currentVersion, toDecode));
    }

    @Override
    public void sync() { }

    @Override
    public void shutdown() { }

    @Override
    public String getConnectionSpec() {
        return hostId;
    }

    @Override
    public IMirror getMirror() {
        return wire;
    }

    private static class MessageEnvelope {

        final LocalNetwork sender;
        final Message msg;
        final RoutingNode recipient;

        MessageEnvelope(LocalNetwork sender, Message msg, RoutingNode recipient) {
            this.sender = sender;
            this.msg = msg;
            this.recipient = recipient;
        }

        void send() {
            ((LocalServiceAddress) recipient.getServiceAddress()).getNetwork().receiveLater(this);
        }
    }

    private static class ReplyEnvelope {

        final LocalNetwork sender;
        final MessageEnvelope parent;
        final Reply reply;

        ReplyEnvelope(LocalNetwork sender, MessageEnvelope parent, Reply reply) {
            this.sender = sender;
            this.parent = parent;
            this.reply = reply;
        }

        void send() {
            parent.sender.receiveLater(this);
        }
    }

}
