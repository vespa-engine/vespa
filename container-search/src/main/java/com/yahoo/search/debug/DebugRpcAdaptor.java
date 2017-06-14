// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.debug;

import com.yahoo.container.osgi.AbstractRpcAdaptor;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Supervisor;
import com.yahoo.fs4.PacketDumper.PacketType;

/**
 * Handles rpc calls for retrieving debug information.
 *
 * @author tonytv
 */
public final class DebugRpcAdaptor extends AbstractRpcAdaptor {
    private static final String debugPrefix = "debug.";

    public void bindCommands(Supervisor supervisor) {
        addTraceMethod(supervisor, "query", PacketType.query);
        addTraceMethod(supervisor, "result", PacketType.result);
        addMethod(supervisor, "output-search-chain", new OutputSearchChain());
        addMethod(supervisor, "backend-statistics", new BackendStatistics());
    }

    private void addTraceMethod(Supervisor supervisor, String name, PacketType packetType) {
        addMethod(supervisor, constructTraceMethodName(name), new TracePackets(packetType));
    }

    private void addMethod(Supervisor supervisor, String name, DebugMethodHandler handler) {
        JrtMethodSignature typeStrings = handler.getSignature();
        supervisor.addMethod(
                new Method(debugPrefix + name,
                        typeStrings.parametersTypes,
                        typeStrings.returnTypes,
                        handler));

    }

    //example: debug.dump-query-packets
    private String constructTraceMethodName(String name) {
        return debugPrefix + "dump-" + name + "-packets";
    }
}
