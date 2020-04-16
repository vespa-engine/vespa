// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.component.Version;
import com.yahoo.jrt.DataValue;
import com.yahoo.jrt.DoubleValue;
import com.yahoo.jrt.Int32Array;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Int64Value;
import com.yahoo.jrt.Int8Value;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringArray;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Values;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.Trace;
import com.yahoo.messagebus.TraceNode;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.text.Utf8Array;

/**
 * Implements the request adapter for method "mbus.send1".
 *
 * @author Simon Thoresen Hult
 */
public class RPCSendV1 extends RPCSend {

    private final String METHOD_NAME = "mbus.send1";
    private final String METHOD_PARAMS = "sssbilsxi";
    private final String METHOD_RETURN = "sdISSsxs";

    @Override
    protected String getReturnSpec() { return METHOD_RETURN; }
    @Override
    protected Method buildMethod() {

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
        return method;
    }
    @Override
    protected Request encodeRequest(Version version, Route route, RPCServiceAddress address, Message msg,
                                    long timeRemaining, byte[] payload, int traceLevel) {
        Request req = new Request(METHOD_NAME);
        Values v = req.parameters();
        v.add(new StringValue(version.toUtf8()));
        v.add(new StringValue(route.toString()));
        v.add(new StringValue(address.getSessionName()));
        v.add(new Int8Value(msg.getRetryEnabled() ? (byte)1 : (byte)0));
        v.add(new Int32Value(msg.getRetry()));
        v.add(new Int64Value(timeRemaining));
        v.add(new StringValue(msg.getProtocol()));
        v.add(new DataValue(payload));
        v.add(new Int32Value(traceLevel));
        return req;
    }

    @Override
    protected Reply createReply(Values ret, String serviceName, Trace trace) {
        Version version = new Version(ret.get(0).asUtf8Array());
        double retryDelay = ret.get(1).asDouble();
        int[] errorCodes = ret.get(2).asInt32Array();
        String[] errorMessages = ret.get(3).asStringArray();
        String[] errorServices = ret.get(4).asStringArray();
        Utf8Array protocolName = ret.get(5).asUtf8Array();
        byte[] payload = ret.get(6).asData();
        String replyTrace = ret.get(7).asString();

        // Make sure that the owner understands the protocol.
        Reply reply = null;
        Error error = null;
        if (payload.length > 0) {
            Object retval = decode(protocolName, version, payload);
            if (retval instanceof Reply) {
                reply = (Reply) retval;
            } else {
                error = (Error) retval;
            }
        }
        if (reply == null) {
            reply = new EmptyReply();
        }
        if (error != null) {
            reply.addError(error);
        }
        reply.setRetryDelay(retryDelay);
        for (int i = 0; i < errorCodes.length && i < errorMessages.length; i++) {
            reply.addError(new Error(errorCodes[i], errorMessages[i],
                    errorServices[i].length() > 0 ? errorServices[i] : serviceName));
        }
        if (trace.getLevel() > 0) {
            trace.getRoot().addChild(TraceNode.decode(replyTrace));
        }
        return reply;
    }

    protected Params toParams(Values args) {
        Params p = new Params();
        p.version = new Version(args.get(0).asUtf8Array());
        p.route = args.get(1).asString();
        p.session = args.get(2).asString();
        p.retryEnabled = (args.get(3).asInt8() != 0);
        p.retry = args.get(4).asInt32();
        p.timeRemaining = args.get(5).asInt64();
        p.protocolName = args.get(6).asUtf8Array();
        p.payload = args.get(7).asData();
        p.traceLevel = args.get(8).asInt32();
        return p;
    }

    @Override
    protected void createResponse(Values ret, Reply reply, Version version, byte [] payload) {
        int[] eCodes = new int[reply.getNumErrors()];
        String[] eMessages = new String[reply.getNumErrors()];
        String[] eServices = new String[reply.getNumErrors()];
        for (int i = 0; i < reply.getNumErrors(); ++i) {
            Error error = reply.getError(i);
            eCodes[i] = error.getCode();
            eMessages[i] = error.getMessage();
            eServices[i] = error.getService() != null ? error.getService() : "";
        }
        ret.add(new StringValue(version.toUtf8()));
        ret.add(new DoubleValue(reply.getRetryDelay()));
        ret.add(new Int32Array(eCodes));
        ret.add(new StringArray(eMessages));
        ret.add(new StringArray(eServices));
        ret.add(new StringValue(reply.getProtocol()));
        ret.add(new DataValue(payload));
        ret.add(new StringValue(reply.getTrace().getRoot() != null ? reply.getTrace().getRoot().encode() : ""));
    }

}
