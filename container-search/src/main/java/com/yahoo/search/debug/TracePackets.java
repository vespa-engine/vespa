// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.debug;

import static com.yahoo.search.debug.SearcherUtils.clusterSearchers;

import java.util.Collection;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.Value;
import com.yahoo.prelude.cluster.ClusterSearcher;
import com.yahoo.fs4.PacketDumper;
import com.yahoo.yolean.Exceptions;

/**
 * Rpc method for enabling packet dumping for a specific packet type.
 *
 * @author tonytv
 */
final class TracePackets implements DebugMethodHandler {
    private final PacketDumper.PacketType packetType;

    public void invoke(Request request) {
        try {
            Collection<ClusterSearcher> searchers = clusterSearchers(request);
            boolean on = request.parameters().get(1).asInt8() != 0;

            for (ClusterSearcher searcher : searchers)
                searcher.dumpPackets(packetType, on);

        } catch (Exception e) {
            request.setError(1000, Exceptions.toMessageString(e));
        }
    }

    TracePackets(PacketDumper.PacketType packetType) {
        this.packetType = packetType;
    }

    public JrtMethodSignature getSignature() {
        String returnTypes = "";
        String parametersTypes = "" + (char)Value.STRING + (char)Value.INT8;
        return new JrtMethodSignature(returnTypes, parametersTypes);
    }
}
