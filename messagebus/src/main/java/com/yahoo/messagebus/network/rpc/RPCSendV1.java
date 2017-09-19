// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.component.Version;
import com.yahoo.jrt.*;
import com.yahoo.jrt.StringValue;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingNode;
import com.yahoo.text.Utf8Array;
import com.yahoo.text.Utf8String;

/**
 * Implements the request adapter for method "mbus.send1".
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class RPCSendV1 implements MethodHandler, ReplyHandler, RequestWaiter, RPCSendAdapter {

    private final String METHOD_NAME = "mbus.send1";
    private final String METHOD_PARAMS = "sssbilsxi";
    private final String METHOD_RETURN = "sdISSsxs";
    private RPCNetwork net = null;
    private String clientIdent = "client";
    private String serverIdent = "server";

    @Override
    public void attach(RPCNetwork net) {
        this.net = net;
        String prefix = net.getIdentity().getServicePrefix();
        if (prefix != null && prefix.length() > 0) {
            clientIdent = "'" + prefix + "'";
            serverIdent = clientIdent;
        }

        Method method = new Method(METHOD_NAME, METHOD_PARAMS, METHOD_RETURN, this);
        method.methodDesc("Send a message bus request and get a reply back.");
        method.paramDesc(0, "version", "The version of the message.")
                .paramDesc(1, "route", "Names of additional hops to visit.")
                .paramDesc(2, "session", "The local session that should receive this message.")
                .paramDesc(3, "retryEnabled", "Whether or not this message can be resent.")
                .paramDesc(4, "retry", "The number of times the sending of this message has been retried.")
                .paramDesc(5, "timeRemaining", "The number of milliseconds until timeout.")
                .paramDesc(6, "protocol", "The name of the protocol that knows how to decode this message.")
                .paramDesc(7, "payload", "The protocol specific message payload.")
                .paramDesc(8, "level", "The trace level of the message.");
        method.returnDesc(0, "version", "The lowest version the message was serialized as.")
                .returnDesc(1, "retryDelay", "The retry request of the reply.")
                .returnDesc(2, "errorCodes", "The reply error codes.")
                .returnDesc(3, "errorMessages", "The reply error messages.")
                .returnDesc(4, "errorServices", "The reply error service names.")
                .returnDesc(5, "protocol", "The name of the protocol that knows how to decode this reply.")
                .returnDesc(6, "payload", "The protocol specific reply payload.")
                .returnDesc(7, "trace", "A string representation of the trace.");
        net.getSupervisor().addMethod(method);
    }

    @Override
    public void send(RoutingNode recipient, Version version, byte[] payload, long timeRemaining) {
        SendContext ctx = new SendContext(recipient, timeRemaining);
        RPCServiceAddress address = (RPCServiceAddress)recipient.getServiceAddress();
        Message msg = recipient.getMessage();
        Route route = new Route(recipient.getRoute());
        Hop hop = route.removeHop(0);

        Request req = new Request(METHOD_NAME);
        req.parameters().add(new StringValue(version.toString()));
        req.parameters().add(new StringValue(route.toString()));
        req.parameters().add(new StringValue(address.getSessionName()));
        req.parameters().add(new Int8Value(msg.getRetryEnabled() ? (byte)1 : (byte)0));
        req.parameters().add(new Int32Value(msg.getRetry()));
        req.parameters().add(new Int64Value(timeRemaining));
        req.parameters().add(new StringValue(msg.getProtocol()));
        req.parameters().add(new DataValue(payload));
        req.parameters().add(new Int32Value(ctx.trace.getLevel()));

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
            net.getOwner().deliverReply(reply, recipient);
        } else {
            req.setContext(ctx);
            address.getTarget().getJRTTarget().invokeAsync(req, ctx.timeout, this);
        }
        req.discardParameters(); // allow garbage collection of request parameters
    }

    @Override
    public void handleRequestDone(Request req) {
        SendContext ctx = (SendContext)req.getContext();
        String serviceName = ((RPCServiceAddress)ctx.recipient.getServiceAddress()).getServiceName();
        Reply reply = null;
        Error error = null;
        if (!req.checkReturnTypes(METHOD_RETURN)) {
            // Map all known JRT errors to the appropriate message bus error.
            reply = new EmptyReply();
            switch (req.errorCode()) {
            case com.yahoo.jrt.ErrorCode.TIMEOUT:
                error = new Error(com.yahoo.messagebus.ErrorCode.TIMEOUT,
                                  "A timeout occured while waiting for '" + serviceName + "' (" +
                                  ctx.timeout + " seconds expired); " + req.errorMessage());
                break;
            case com.yahoo.jrt.ErrorCode.CONNECTION:
                error = new Error(com.yahoo.messagebus.ErrorCode.CONNECTION_ERROR,
                                  "A connection error occured for '" + serviceName + "'; " + req.errorMessage());
                break;
            default:
                error = new Error(com.yahoo.messagebus.ErrorCode.NETWORK_ERROR,
                                  "A network error occured for '" + serviceName + "'; " + req.errorMessage());
            }
        } else {
            // Retrieve all reply components from JRT request object.
            Version version = new Version(req.returnValues().get(0).asUtf8Array());
            double retryDelay = req.returnValues().get(1).asDouble();
            int[] errorCodes = req.returnValues().get(2).asInt32Array();
            String[] errorMessages = req.returnValues().get(3).asStringArray();
            String[] errorServices = req.returnValues().get(4).asStringArray();
            Utf8Array protocolName = req.returnValues().get(5).asUtf8Array();
            byte[] payload = req.returnValues().get(6).asData();
            String replyTrace = req.returnValues().get(7).asString();

            // Make sure that the owner understands the protocol.
            if (payload.length > 0) {
                Protocol protocol = net.getOwner().getProtocol(protocolName);
                if (protocol != null) {
                    Routable routable = protocol.decode(version, payload);
                    if (routable != null) {
                        if (routable instanceof Reply) {
                            reply = (Reply)routable;
                        } else {
                            error = new Error(com.yahoo.messagebus.ErrorCode.DECODE_ERROR,
                                              "Payload decoded to a reply when expecting a message.");
                        }
                    } else {
                        error = new Error(com.yahoo.messagebus.ErrorCode.DECODE_ERROR,
                                          "Protocol '" + protocol.getName() + "' failed to decode routable.");
                    }
                } else {
                    error = new Error(com.yahoo.messagebus.ErrorCode.UNKNOWN_PROTOCOL,
                                      "Protocol '" + protocolName + "' is not known by " + serverIdent + ".");
                }
            }
            if (reply == null) {
                reply = new EmptyReply();
            }
            reply.setRetryDelay(retryDelay);
            for (int i = 0; i < errorCodes.length && i < errorMessages.length; i++) {
                reply.addError(new Error(errorCodes[i],
                                         errorMessages[i],
                                         errorServices[i].length() > 0 ? errorServices[i] : serviceName));
            }
            if (ctx.trace.getLevel() > 0) {
                ctx.trace.getRoot().addChild(TraceNode.decode(replyTrace));
            }
        }
        if (ctx.trace.shouldTrace(TraceLevel.SEND_RECEIVE)) {
            ctx.trace.trace(TraceLevel.SEND_RECEIVE,
                            "Reply (type " + reply.getType() + ") received at " + clientIdent + ".");
        }
        reply.getTrace().swap(ctx.trace);
        if (error != null) {
            reply.addError(error);
        }
        net.getOwner().deliverReply(reply, ctx.recipient);
    }

    @Override
    public void invoke(Request request) {
        request.detach();
        Version version = new Version(request.parameters().get(0).asUtf8Array());
        String route = request.parameters().get(1).asString();
        String session = request.parameters().get(2).asString();
        boolean retryEnabled = (request.parameters().get(3).asInt8() != 0);
        int retry = request.parameters().get(4).asInt32();
        long timeRemaining = request.parameters().get(5).asInt64();
        Utf8Array protocolName = request.parameters().get(6).asUtf8Array();
        byte[] payload = request.parameters().get(7).asData();
        int traceLevel = request.parameters().get(8).asInt32();

        request.discardParameters(); // allow garbage collection of request parameters

        // Make sure that the owner understands the protocol.
        Protocol protocol = net.getOwner().getProtocol(protocolName);
        if (protocol == null) {
            replyError(request, version, traceLevel,
                       new com.yahoo.messagebus.Error(ErrorCode.UNKNOWN_PROTOCOL,
                                                      "Protocol '" + protocolName + "' is not known by " + serverIdent + "."));
            return;
        }
        Routable routable = protocol.decode(version, payload);
        if (routable == null) {
            replyError(request, version, traceLevel,
                       new Error(ErrorCode.DECODE_ERROR,
                                 "Protocol '" + protocol.getName() + "' failed to decode routable."));
            return;
        }
        if (routable instanceof Reply) {
            replyError(request, version, traceLevel,
                       new Error(ErrorCode.DECODE_ERROR,
                                 "Payload decoded to a reply when expecting a message."));
            return;
        }
        Message msg = (Message)routable;
        if (route != null && route.length() > 0) {
            msg.setRoute(net.getRoute(route));
        }
        msg.setContext(new ReplyContext(request, version));
        msg.pushHandler(this);
        msg.setRetryEnabled(retryEnabled);
        msg.setRetry(retry);
        msg.setTimeReceivedNow();
        msg.setTimeRemaining(timeRemaining);
        msg.getTrace().setLevel(traceLevel);
        if (msg.getTrace().shouldTrace(TraceLevel.SEND_RECEIVE)) {
            msg.getTrace().trace(TraceLevel.SEND_RECEIVE,
                                 "Message (type " + msg.getType() + ") received at " + serverIdent + " for session '" + session + "'.");
        }
        net.getOwner().deliverMessage(msg, session);
    }

    @Override
    public void handleReply(Reply reply) {
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
            Protocol protocol = net.getOwner().getProtocol(reply.getProtocol());
            if (protocol != null) {
                payload = protocol.encode(ctx.version, reply);
            }
            if (payload == null || payload.length == 0) {
                reply.addError(new Error(ErrorCode.ENCODE_ERROR,
                                         "An error occured while encoding the reply."));
            }
        }
        int[] eCodes = new int[reply.getNumErrors()];
        String[] eMessages = new String[reply.getNumErrors()];
        String[] eServices = new String[reply.getNumErrors()];
        for (int i = 0; i < reply.getNumErrors(); ++i) {
            Error error = reply.getError(i);
            eCodes[i] = error.getCode();
            eMessages[i] = error.getMessage();
            eServices[i] = error.getService() != null ? error.getService() : "";
        }
        ctx.request.returnValues().add(new StringValue(ctx.version.toString()));
        ctx.request.returnValues().add(new DoubleValue(reply.getRetryDelay()));
        ctx.request.returnValues().add(new Int32Array(eCodes));
        ctx.request.returnValues().add(new StringArray(eMessages));
        ctx.request.returnValues().add(new StringArray(eServices));
        ctx.request.returnValues().add(new StringValue(reply.getProtocol()));
        ctx.request.returnValues().add(new DataValue(payload));
        ctx.request.returnValues().add(new StringValue(
                reply.getTrace().getRoot() != null ?
                reply.getTrace().getRoot().encode() :
                ""));
        ctx.request.returnRequest();
    }

    /**
     * Send an error reply for a given request.
     *
     * @param request    The JRT request to reply to.
     * @param version    The version to serialize for.
     * @param traceLevel The trace level to set in the reply.
     * @param err        The error to reply with.
     */
    private void replyError(Request request, Version version, int traceLevel, Error err) {
        Reply reply = new EmptyReply();
        reply.setContext(new ReplyContext(request, version));
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

        public ReplyContext(Request request, Version version) {
            this.request = request;
            this.version = version;
        }
    }
}
