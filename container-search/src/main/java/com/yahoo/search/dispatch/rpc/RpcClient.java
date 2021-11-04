// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

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

/**
 * A client which uses rpc request to search nodes to implement the Client API.
 *
 * @author bratseth
 */
class RpcClient implements Client {

    private final Supervisor supervisor;

    public RpcClient(String name, int transportThreads) {
        supervisor = new Supervisor(new Transport(name, transportThreads));
    }

    @Override
    public void close() {
        supervisor.transport().shutdown().join();
    }

    @Override
    public NodeConnection createConnection(String hostname, int port) {
        return new RpcNodeConnection(hostname, port, supervisor);
    }

    private static class RpcNodeConnection implements NodeConnection {

        // Information about the connected node
        private final Supervisor supervisor;
        private final String hostname;
        private final int port;
        private final String description;

        // The current shared connection. This will be recycled when it becomes invalid.
        // All access to this must be synchronized
        private Target target;

        public RpcNodeConnection(String hostname, int port, Supervisor supervisor) {
            this.supervisor = supervisor;
            this.hostname = hostname;
            this.port = port;
            description = "rpc node connection to " + hostname + ":" + port;
            target = supervisor.connect(new Spec(hostname, port));
        }

        @Override
        public void request(String rpcMethod, CompressionType compression, int uncompressedLength, byte[] compressedPayload,
                            ResponseReceiver responseReceiver, double timeoutSeconds) {
            Request request = new Request(rpcMethod);
            request.parameters().add(new Int8Value(compression.getCode()));
            request.parameters().add(new Int32Value(uncompressedLength));
            request.parameters().add(new DataValue(compressedPayload));

            invokeAsync(request, timeoutSeconds, new RpcProtobufResponseWaiter(this, responseReceiver));
        }

        private void invokeAsync(Request req, double timeout, RequestWaiter waiter) {
            // TODO: Consider replacing this by a watcher on the target
            synchronized(this) { // ensure we have exactly 1 valid connection across threads
                if (! target.isValid()) {
                    target = supervisor.connect(new Spec(hostname, port));
                }
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

    private static class RpcProtobufResponseWaiter implements RequestWaiter {

        /** The node to which we made the request we are waiting for - for error messages only */
        private final RpcNodeConnection node;

        /** The handler to which the response is forwarded */
        private final ResponseReceiver handler;

        public RpcProtobufResponseWaiter(RpcNodeConnection node, ResponseReceiver handler) {
            this.node = node;
            this.handler = handler;
        }

        @Override
        public void handleRequestDone(Request requestWithResponse) {
            if (requestWithResponse.isError()) {
                handler.receive(ResponseOrError.fromError("Error response from " + node + ": " + requestWithResponse.errorMessage()));
                return;
            }

            Values returnValues = requestWithResponse.returnValues();
            if (returnValues.size() < 3) {
                handler.receive(ResponseOrError.fromError(
                        "Invalid getDocsums response from " + node + ": Expected 3 return arguments, got " + returnValues.size()));
                return;
            }

            byte compression = returnValues.get(0).asInt8();
            int uncompressedSize = returnValues.get(1).asInt32();
            byte[] compressedPayload = returnValues.get(2).asData();
            handler.receive(ResponseOrError.fromResponse(new ProtobufResponse(compression, uncompressedSize, compressedPayload)));
        }

    }

}
