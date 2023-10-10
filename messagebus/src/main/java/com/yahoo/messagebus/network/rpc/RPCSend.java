// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.component.Version;

import com.yahoo.jrt.Method;
import com.yahoo.jrt.MethodHandler;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Values;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Routable;
import com.yahoo.messagebus.Trace;
import com.yahoo.messagebus.TraceLevel;
import com.yahoo.messagebus.network.NetworkOwner;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingNode;
import com.yahoo.text.Utf8Array;

/**
 * Implements the request adapter for method "mbus.send1/mbus.slime".
 *
 * @author baldersheim
 */
public abstract class RPCSend implements MethodHandler, ReplyHandler, RequestWaiter, RPCSendAdapter {

    private final RPCNetwork net;
    private final String clientIdent;
    private final String serverIdent;

    protected abstract Method buildMethod();
    protected abstract String getReturnSpec();
    protected abstract Request encodeRequest(Version version, Route route, RPCServiceAddress address, Message msg,
                                             long timeRemaining, byte[] payload, int traceLevel);
    protected abstract Reply createReply(Values ret, String serviceName, Trace trace);
    protected abstract Params toParams(Values req);
    protected abstract void createResponse(Values ret, Reply reply, Version version, byte [] payload);

    protected RPCSend(RPCNetwork net) {
        this.net = net;
        String prefix = net.getIdentity().getServicePrefix();
        if (prefix != null && prefix.length() > 0) {
            this.serverIdent = this.clientIdent = "'" + prefix + "'";
        }
        else {
            this.clientIdent = "client";
            this.serverIdent = "server";
        }
        net.getSupervisor().addMethod(buildMethod());
    }

    @Override
    public final void send(RoutingNode recipient, Version version, byte[] payload, long timeRemaining) {
        SendContext ctx = new SendContext(recipient, timeRemaining);
        RPCServiceAddress address = (RPCServiceAddress)recipient.getServiceAddress();
        Message msg = recipient.getMessage();
        Route route = new Route(recipient.getRoute());
        Hop hop = route.removeHop(0);

        Request req = encodeRequest(version, route, address,msg, timeRemaining, payload, ctx.trace.getLevel());

        if (ctx.trace.shouldTrace(TraceLevel.SEND_RECEIVE)) {
            ctx.trace.trace(TraceLevel.SEND_RECEIVE,
                    "Sending message (version " + version + ") from " + clientIdent + " to '" +
                            address.getServiceName() + "' with " + ctx.timeout + " seconds timeout.");
        }

        if (hop.getIgnoreResult()) {
            address.getTarget().getJRTTarget().invokeVoid(req);
            if (ctx.trace.shouldTrace(TraceLevel.SEND_RECEIVE)) {
                ctx.trace.trace(TraceLevel.SEND_RECEIVE,
                        "Not waiting for a reply from '" + address.getServiceName() + "'.");
            }
            Reply reply = new EmptyReply();
            reply.getTrace().swap(ctx.trace);
            recipient.handleReply(reply);
        } else {
            req.setContext(ctx);
            address.getTarget().getJRTTarget().invokeAsync(req, ctx.timeout, this);
        }
        req.discardParameters(); // allow garbage collection of request parameters
    }

    protected final Object decode(Utf8Array protocolName, Version version, byte [] payload) {
        Protocol protocol = net.getOwner().getProtocol(protocolName);
        if (protocol != null) {
            Routable routable = protocol.decode(version, payload);
            if (routable != null) {
                if (routable instanceof Reply) {
                    return routable;
                } else {
                    return new Error(ErrorCode.DECODE_ERROR,
                            "Payload decoded to a reply when expecting a message.");
                }
            } else {
                return new Error(ErrorCode.DECODE_ERROR,
                        "Protocol '" + protocol.getName() + "' failed to decode routable.");
            }
        } else {
            return new Error(ErrorCode.UNKNOWN_PROTOCOL,
                    "Protocol '" + protocolName + "' is not known by " + serverIdent + ".");
        }
    }

    @Override
    public final void handleRequestDone(Request req) {
        net.getExecutor().execute(() -> doRequestDone(req));
    }

    private void doRequestDone(Request req) {
        SendContext ctx = (SendContext)req.getContext();
        String serviceName = ((RPCServiceAddress)ctx.recipient.getServiceAddress()).getServiceName();
        Reply reply;
        Error error = null;
        if (!req.checkReturnTypes(getReturnSpec())) {
            // Map all known JRT errors to the appropriate message bus error.
            reply = new EmptyReply();
            switch (req.errorCode()) {
                case com.yahoo.jrt.ErrorCode.TIMEOUT:
                    error = new Error(ErrorCode.TIMEOUT,
                            "A timeout occurred while waiting for '" + serviceName + "' (" +
                                    ctx.timeout + " seconds expired); " + req.errorMessage());
                    break;
                case com.yahoo.jrt.ErrorCode.CONNECTION:
                    error = new Error(ErrorCode.CONNECTION_ERROR,
                            "A connection error occurred for '" + serviceName + "'; " + req.errorMessage());
                    break;
                default:
                    error = new Error(ErrorCode.NETWORK_ERROR,
                            "A network error occurred for '" + serviceName + "'; " + req.errorMessage());
            }
        } else {
            reply = createReply(req.returnValues(), serviceName, ctx.trace);
        }
        if (ctx.trace.shouldTrace(TraceLevel.SEND_RECEIVE)) {
            ctx.trace.trace(TraceLevel.SEND_RECEIVE,
                    "Reply (type " + reply.getType() + ") received at " + clientIdent + ".");
        }
        reply.getTrace().swap(ctx.trace);
        if (error != null) {
            reply.addError(error);
        }
        ctx.recipient.handleReply(reply);
    }

    protected final class Params {
        Version version;
        String route;
        String session;
        boolean retryEnabled;
        int retry;
        long timeRemaining;
        Utf8Array protocolName;
        byte [] payload;
        int traceLevel;
    }

    @Override
    public final void invoke(Request request) {
        request.detach();
        net.getExecutor().execute(() -> doInvoke(request));
    }

    private void doInvoke(Request request) {
        Params p = toParams(request.parameters());

        request.discardParameters(); // allow garbage collection of request parameters

        // Make sure that the owner understands the protocol.
        Protocol protocol = net.getOwner().getProtocol(p.protocolName);
        if (protocol == null) {
            replyError(request, p.version, protocol, p.traceLevel,
                    new Error(ErrorCode.UNKNOWN_PROTOCOL,
                            "Protocol '" + p.protocolName + "' is not known by " + serverIdent + "."));
            return;
        }
        Routable routable = protocol.decode(p.version, p.payload);
        if (routable == null) {
            replyError(request, p.version, protocol, p.traceLevel,
                    new Error(ErrorCode.DECODE_ERROR,
                            "Protocol '" + protocol.getName() + "' failed to decode routable."));
            return;
        }
        if (routable instanceof Reply) {
            replyError(request, p.version, protocol, p.traceLevel,
                    new Error(ErrorCode.DECODE_ERROR,
                            "Payload decoded to a reply when expecting a message."));
            return;
        }
        Message msg = (Message)routable;
        if (p.route != null && p.route.length() > 0) {
            msg.setRoute(net.getRoute(p.route));
        }
        msg.setContext(new ReplyContext(request, p.version, protocol));
        msg.pushHandler(this);
        msg.setRetryEnabled(p.retryEnabled);
        msg.setRetry(p.retry);
        msg.setTimeReceivedNow();
        msg.setTimeRemaining(p.timeRemaining);
        msg.getTrace().setLevel(p.traceLevel);
        if (msg.getTrace().shouldTrace(TraceLevel.SEND_RECEIVE)) {
            msg.getTrace().trace(TraceLevel.SEND_RECEIVE,
                    "Message (type " + msg.getType() + ") received at " + serverIdent + " for session '" + p.session + "'.");
        }
        net.getOwner().deliverMessage(msg, p.session);
    }

    @Override
    public final void handleReply(Reply reply) {
        ReplyContext ctx = (ReplyContext)reply.getContext();
        reply.setContext(null);

        // Add trace information.
        if (reply.getTrace().shouldTrace(TraceLevel.SEND_RECEIVE)) {
            reply.getTrace().trace(TraceLevel.SEND_RECEIVE,
                    "Sending reply (version " + ctx.version + ") from " + serverIdent + ".");
        }

        // Encode and return the reply through the RPC request.
        byte[] payload = new byte[0];
        if (reply.getType() != 0) {
            if (ctx.protocol != null) {
                payload = ctx.protocol.encode(ctx.version, reply);
            }
            if (payload == null || payload.length == 0) {
                reply.addError(new Error(ErrorCode.ENCODE_ERROR,
                        "An error occurred while encoding the reply."));
            }
        }
        createResponse(ctx.request.returnValues(), reply, ctx.version, payload);
        ctx.request.returnRequest();
    }

    /**
     * Send an error reply for a given request.
     *
     * @param request    The JRT request to reply to.
     * @param version    The version to serialize for.
     * @param traceLevel The trace level to set in the reply.
     * @param protocol   The message protocol to serialize with.
     * @param err        The error to reply with.
     */
    private void replyError(Request request, Version version, Protocol protocol, int traceLevel, Error err) {
        Reply reply = new EmptyReply();
        reply.setContext(new ReplyContext(request, version, protocol));
        reply.getTrace().setLevel(traceLevel);
        reply.addError(err);
        handleReply(reply);
    }

    private static class SendContext {

        final RoutingNode recipient;
        final Trace trace;
        final double timeout;

        SendContext(RoutingNode recipient, long timeRemaining) {
            this.recipient = recipient;
            trace = new Trace(recipient.getTrace().getLevel());
            timeout = timeRemaining * 0.001;
        }
    }

    private static class ReplyContext {

        final Request request;
        final Version version;
        final Protocol protocol;

        ReplyContext(Request request, Version version, Protocol protocol) {
            this.request = request;
            this.version = version;
            this.protocol = protocol;
        }
    }
}
