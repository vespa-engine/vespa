// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.compress.CompressionType;
import com.yahoo.jrt.DataValue;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Int8Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.Values;
import com.yahoo.prelude.fastsearch.FastHit;

import java.util.List;

/**
 * A client which uses rpc request to search nodes to implement the Client API.
 *
 * @author bratseth
 */
class RpcClient implements Client {

    private final Supervisor supervisor = new Supervisor(new Transport());

    @Override
    public NodeConnection createConnection(String hostname, int port) {
        return new RpcNodeConnection(hostname, port, supervisor);
    }

    @Override
    public void getDocsums(List<FastHit> hits, NodeConnection node, CompressionType compression, int uncompressedLength,
                           byte[] compressedSlime, RpcFillInvoker.GetDocsumsResponseReceiver responseReceiver, double timeoutSeconds) {
        Request request = new Request("proton.getDocsums");
        request.parameters().add(new Int8Value(compression.getCode()));
        request.parameters().add(new Int32Value(uncompressedLength));
        request.parameters().add(new DataValue(compressedSlime));

        request.setContext(hits);
        RpcNodeConnection rpcNode = ((RpcNodeConnection) node);
        rpcNode.invokeAsync(request, timeoutSeconds, new RpcResponseWaiter(rpcNode, responseReceiver));
    }

    private static class RpcNodeConnection implements NodeConnection {

        // Information about the connected node
        private final Supervisor supervisor;
        private final String hostname;
        private final int port;
        private final String description;

        // The current shared connection. This will be recycled when it becomes invalid.
        // All access to this must be synchronized
        private Target target = null;

        public RpcNodeConnection(String hostname, int port, Supervisor supervisor) {
            this.supervisor = supervisor;
            this.hostname = hostname;
            this.port = port;
            description = "rpc node connection to " + hostname + ":" + port;
        }

        public void invokeAsync(Request req, double timeout, RequestWaiter waiter) {
            // TODO: Consider replacing this by a watcher on the target
            synchronized(this) { // ensure we have exactly 1 valid connection across threads
                if (target == null || ! target.isValid())
                    target = supervisor.connect(new Spec(hostname, port));
            }
            target.invokeAsync(req, timeout, waiter);
        }

        @Override
        public void close() {
            target.close();
        }

        @Override
        public String toString() {
            return description;
        }

    }

    private static class RpcResponseWaiter implements RequestWaiter {

        /** The node to which we made the request we are waiting for - for error messages only */
        private final RpcNodeConnection node;

        /** The handler to which the response is forwarded */
        private final RpcFillInvoker.GetDocsumsResponseReceiver handler;

        public RpcResponseWaiter(RpcNodeConnection node, RpcFillInvoker.GetDocsumsResponseReceiver handler) {
            this.node = node;
            this.handler = handler;
        }

        @Override
        public void handleRequestDone(Request requestWithResponse) {
            if (requestWithResponse.isError()) {
                handler.receive(GetDocsumsResponseOrError.fromError("Error response from " + node + ": " +
                                                                    requestWithResponse.errorMessage()));
                return;
            }

            Values returnValues = requestWithResponse.returnValues();
            if (returnValues.size() < 3) {
                handler.receive(GetDocsumsResponseOrError.fromError("Invalid getDocsums response from " + node +
                                                                    ": Expected 3 return arguments, got " +
                                                                    returnValues.size()));
                return;
            }

            byte compression = returnValues.get(0).asInt8();
            int uncompressedSize = returnValues.get(1).asInt32();
            byte[] compressedSlimeBytes = returnValues.get(2).asData();
            List<FastHit> hits = (List<FastHit>) requestWithResponse.getContext();
            handler.receive(GetDocsumsResponseOrError.fromResponse(new GetDocsumsResponse(compression,
                                                                                          uncompressedSize,
                                                                                          compressedSlimeBytes,
                                                                                          hits)));
        }

    }

}
