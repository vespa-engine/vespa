// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.google.protobuf.InvalidProtocolBufferException;
import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.prelude.Pong;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.rpc.Client.ProtobufResponse;
import com.yahoo.search.dispatch.rpc.Client.ResponseOrError;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.Pinger;
import com.yahoo.search.dispatch.searchcluster.PongHandler;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.yolean.Exceptions;

import java.util.logging.Logger;

public class RpcPing implements Pinger, Client.ResponseReceiver {

    private static final Logger log = Logger.getLogger(RpcPing.class.getName());
    private static final String RPC_METHOD = "vespa.searchprotocol.ping";
    private static final CompressionType PING_COMPRESSION = CompressionType.NONE;
    private static final boolean triggeredClassLoading = ErrorMessage.createBackendCommunicationError("TriggerClassLoading") instanceof ErrorMessage;

    private final Node node;
    private final RpcConnectionPool connectionPool;
    private final ClusterMonitor<Node> clusterMonitor;
    private final long pingSequenceId;
    private final PongHandler pongHandler;
    private final Compressor compressor;

    public RpcPing(Node node, ClusterMonitor<Node> clusterMonitor, RpcConnectionPool connectionPool, PongHandler pongHandler, Compressor compressor) {
        this.node = node;
        this.connectionPool = connectionPool;
        this.clusterMonitor = clusterMonitor;
        this.pingSequenceId = node.createPingSequenceId();
        this.pongHandler = pongHandler;
        this.compressor = compressor;
    }

    @Override
    public void ping() {
        try {
            sendPing();
        } catch (RuntimeException e) {
            receive(ResponseOrError.fromError("Exception when pinging " + node + ": " + Exceptions.toMessageString(e)));
        }
    }

    private record Reply(Pong pong, boolean supportsCorrelatedQueries) { }

    private static Reply failure(ErrorMessage error) {
        return new Reply(new Pong(error), false);
    }

    private Reply toPong(ResponseOrError<ProtobufResponse> responseOrError) {
        if (responseOrError == null) {
            return failure(ErrorMessage.createNoAnswerWhenPingingNode("Timed out waiting for pong from " + node));
        } else if (responseOrError.error().isPresent()) {
            return failure(ErrorMessage.createBackendCommunicationError(responseOrError.error().get()));
        }

        try {
            return decodeReply(responseOrError.response().get());
        } catch (InvalidProtocolBufferException | RuntimeException e) {
            return failure(ErrorMessage.createBackendCommunicationError(Exceptions.toMessageString(e)));
        }
    }

    private void sendPing() {
        var connection = connectionPool.getConnection(node.key());
        var ping = SearchProtocol.MonitorRequest.newBuilder().build().toByteArray();
        double timeoutSeconds = ((double) clusterMonitor.getConfiguration().getRequestTimeout()) / 1000.0;
        Compressor.Compression compressionResult = compressor.compress(PING_COMPRESSION, ping);
        connection.request(RPC_METHOD, compressionResult.type(), ping.length, compressionResult.data(), this, timeoutSeconds);
    }

    private Reply decodeReply(ProtobufResponse response) throws InvalidProtocolBufferException {
        CompressionType compression = CompressionType.valueOf(response.compression());
        byte[] responseBytes = compressor.decompress(response.compressedPayload(), compression, response.uncompressedSize());
        var reply = SearchProtocol.MonitorReply.parseFrom(responseBytes);

        if (reply.getDistributionKey() != node.key()) {
            return failure(ErrorMessage.createBackendCommunicationError("Expected pong from node id " + node.key() +
                                                                         ", response is from id " + reply.getDistributionKey()));
        } else if (!reply.getOnline()) {
            return failure(ErrorMessage.createBackendCommunicationError("Node id " + node.key() + " reports being offline"));
        } else {
            return new Reply(new Pong(reply.getActiveDocs(), reply.getTargetActiveDocs(), reply.getIsBlockingWrites()),
                             reply.getSupportsCorrelatedQueries());
        }
    }

    @Override
    public void receive(ResponseOrError<ProtobufResponse> response) {
        if (clusterMonitor.isClosed() && ! triggeredClassLoading) return;
        Reply reply = toPong(response);
        // Accept the sequence and publish its state together, after decoding completes.
        synchronized (node) {
            if (node.isLastReceivedPong(pingSequenceId)) {
                node.setSupportsCorrelatedQueries(reply.supportsCorrelatedQueries());
                pongHandler.handle(reply.pong());
            } else {
                // TODO: Reduce to debug or remove once we have enumerated what happens here.
                log.info("Pong " + pingSequenceId + " from node " + node.key() + " in group " + node.group() +
                         " with hostname " + node.hostname() + " received too late, latest is " + node.getLastReceivedPongId());
            }
        }
    }

}
