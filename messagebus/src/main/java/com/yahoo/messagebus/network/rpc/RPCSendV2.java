// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.component.Version;
import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.jrt.DataValue;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Int8Value;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Values;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.Trace;
import com.yahoo.messagebus.TraceNode;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.slime.BinaryFormat;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.text.Utf8;
import com.yahoo.text.Utf8Array;

/**
 * Implements the request adapter for method "mbus.sendslime".
 *
 * @author baldersheim
 */
public class RPCSendV2 extends RPCSend {

    private final static String METHOD_NAME = "mbus.slime";
    private final static String METHOD_PARAMS = "bix";
    private final static String METHOD_RETURN = "bix";
    private final Compressor compressor = new Compressor(CompressionType.LZ4, 3, 90, 1024);

    @Override
    protected String getReturnSpec() { return METHOD_RETURN; }
    @Override
    protected Method buildMethod() {

        Method method = new Method(METHOD_NAME, METHOD_PARAMS, METHOD_RETURN, this);
        method.methodDesc("Send a message bus request and get a reply back.");
        method.paramDesc(0, "encoding", "Encoding type.")
                .paramDesc(1, "decodedSize", "Number of bytes after decoding.")
                .paramDesc(2, "payload", "Slime encoded payload.");
        method.returnDesc(0, "encoding", "Encoding type.")
                .returnDesc(1, "decodedSize", "Number of bytes after decoding.")
                .returnDesc(2, "payload", "Slime encoded payload.");
        return method;
    }
    private static final String VERSION_F = new String("version");
    private static final String ROUTE_F = new String("route");
    private static final String SESSION_F = new String("session");
    private static final String PROTOCOL_F = new String("prot");
    private static final String TRACELEVEL_F = new String("tracelevel");
    private static final String TRACE_F = new String("trace");
    private static final String USERETRY_F = new String("useretry");
    private static final String RETRY_F = new String("retry");
    private static final String RETRYDELAY_F = new String("retrydelay");
    private static final String TIMEREMAINING_F = new String("timeleft");
    private static final String ERRORS_F = new String("errors");
    private static final String SERVICE_F = new String("service");
    private static final String CODE_F = new String("code");
    private static final String BLOB_F = new String("msg");
    private static final String MSG_F = new String("msg");

    @Override
    protected Request encodeRequest(Version version, Route route, RPCServiceAddress address, Message msg,
                                    long timeRemaining, byte[] payload, int traceLevel) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();

        root.setString(VERSION_F, version.toString());
        root.setString(ROUTE_F, route.toString());
        root.setString(SESSION_F, address.getSessionName());
        root.setString(PROTOCOL_F, msg.getProtocol().toString());
        root.setBool(USERETRY_F, msg.getRetryEnabled());
        root.setLong(RETRY_F, msg.getRetry());
        root.setLong(TIMEREMAINING_F, msg.getTimeRemaining());
        root.setLong(TRACELEVEL_F, traceLevel);
        root.setData(BLOB_F, payload);

        byte[] serializedSlime = BinaryFormat.encode(slime);
        Compressor.Compression compressionResult = compressor.compress(serializedSlime);
        Request req = new Request(METHOD_NAME);
        Values v = req.parameters();

        v.add(new Int8Value(compressionResult.type().getCode()));
        v.add(new Int32Value(compressionResult.uncompressedSize()));
        v.add(new DataValue(compressionResult.data()));

        return req;
    }

    @Override
    protected Reply createReply(Values ret, String serviceName, Trace trace) {
        CompressionType compression = CompressionType.valueOf(ret.get(0).asInt8());
        byte[] slimeBytes = compressor.decompress(ret.get(2).asData(), compression, ret.get(1).asInt32());
        Slime slime = BinaryFormat.decode(slimeBytes);
        Inspector root = slime.get();

        Version version = new Version(root.field(VERSION_F).asString());
        byte[] payload = root.field(BLOB_F).asData();

        // Make sure that the owner understands the protocol.
        Reply reply = null;
        Error error = null;
        if (payload.length > 0) {
            Object retval = decode(new Utf8Array(root.field(PROTOCOL_F).asUtf8()), version, payload);
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
        reply.setRetryDelay(root.field(RETRYDELAY_F).asDouble());

        Inspector errors = root.field(ERRORS_F);
        for (int i = 0; i < errors.entries(); i++) {
            Inspector e = errors.entry(i);
            String service = e.field(SERVICE_F).asString();
            reply.addError(new Error((int)e.field(CODE_F).asLong(), e.field(MSG_F).asString(),
                    (service != null && service.length() > 0) ? service : serviceName));
        }
        if (trace.getLevel() > 0) {
            trace.getRoot().addChild(TraceNode.decode(root.field(TRACE_F).asString()));
        }
        return reply;
    }

    protected Params toParams(Values args) {
        CompressionType compression = CompressionType.valueOf(args.get(0).asInt8());
        byte[] slimeBytes = compressor.decompress(args.get(2).asData(), compression, args.get(1).asInt32());
        Slime slime = BinaryFormat.decode(slimeBytes);
        Inspector root = slime.get();
        Params p = new Params();
        p.version = new Version(root.field(VERSION_F).asString());
        p.route = root.field(ROUTE_F).asString();
        p.session = root.field(SESSION_F).asString();
        p.retryEnabled = root.field(USERETRY_F).asBool();
        p.retry = (int)root.field(RETRY_F).asLong();
        p.timeRemaining = root.field(TIMEREMAINING_F).asLong();
        p.protocolName = new Utf8Array(Utf8.toBytes(root.field(PROTOCOL_F).asString()));
        p.payload = root.field(BLOB_F).asData();
        p.traceLevel = (int)root.field(TRACELEVEL_F).asLong();
        return p;
    }

    @Override
    protected void createReponse(Values ret, Reply reply, Version version, byte [] payload) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();

        root.setString(VERSION_F, version.toString());
        root.setDouble(RETRYDELAY_F, reply.getRetryDelay());
        root.setString(PROTOCOL_F, reply.getProtocol().toString());
        root.setData(BLOB_F, payload);
        if (reply.getTrace().getLevel() > 0) {
            root.setString(TRACE_F, reply.getTrace().getRoot().encode());
        }

        if (reply.getNumErrors() > 0) {
            Cursor array = root.setArray(ERRORS_F);
            for (int i = 0; i < reply.getNumErrors(); i++) {
                Cursor e = array.addObject();
                Error mbusE = reply.getError(i);
                e.setLong(CODE_F, mbusE.getCode());
                e.setString(MSG_F, mbusE.getMessage());
                if (mbusE.getService() != null) {
                    e.setString(SERVICE_F, mbusE.getService());
                }
            }
        }

        byte[] serializedSlime = BinaryFormat.encode(slime);
        Compressor.Compression compressionResult = compressor.compress(serializedSlime);

        ret.add(new Int8Value(compressionResult.type().getCode()));
        ret.add(new Int32Value(compressionResult.uncompressedSize()));
        ret.add(new DataValue(compressionResult.data()));
    }

}
