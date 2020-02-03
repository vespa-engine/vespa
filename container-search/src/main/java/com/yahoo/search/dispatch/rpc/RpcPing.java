// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class RpcPing implements Pinger, Client.ResponseReceiver {

    private static final Logger log = Logger.getLogger(RpcPing.class.getName());
    private static final String RPC_METHOD = "vespa.searchprotocol.ping";
    private static final CompressionType PING_COMPRESSION = CompressionType.NONE;

    private final Node node;
    private final RpcResourcePool resourcePool;
    private final ClusterMonitor<Node> clusterMonitor;
    private final long pingSequenceId;
    private final AtomicReference<PongHandler> handler = new AtomicReference<>();

    public RpcPing(Node node, ClusterMonitor<Node> clusterMonitor, RpcResourcePool rpcResourcePool) {
        this.node = node;
        this.resourcePool = rpcResourcePool;
        this.clusterMonitor = clusterMonitor;
        pingSequenceId = node.createPingSequenceId();
    }

    @Override
    public void ping(PongHandler handler) {
        try {
            sendPing(handler);
        } catch (RuntimeException e) {
            handler.handle(new Pong(ErrorMessage.createBackendCommunicationError("Exception when pinging " + node +
                                                                                 ": " + Exceptions.toMessageString(e))));
        }
    }

    private Pong toPong(ResponseOrError<ProtobufResponse> responseOrError) {
        if (responseOrError == null) {
            return new Pong(ErrorMessage.createNoAnswerWhenPingingNode("Timed out waiting for pong from " + node));
        } else if (responseOrError.error().isPresent()) {
            return new Pong(ErrorMessage.createBackendCommunicationError(responseOrError.error().get()));
        }

        try {
            return decodeReply(responseOrError.response().get());
        } catch (InvalidProtocolBufferException e) {
            return new Pong(ErrorMessage.createBackendCommunicationError(e.getMessage()));
        }
    }

    private void sendPing(PongHandler handler) {
        this.handler.set(handler);
        var connection = resourcePool.getConnection(node.key());
        var ping = SearchProtocol.MonitorRequest.newBuilder().build().toByteArray();
        double timeoutSeconds = ((double) clusterMonitor.getConfiguration().getRequestTimeout()) / 1000.0;
        Compressor.Compression compressionResult = resourcePool.compressor().compress(PING_COMPRESSION, ping);
        connection.request(RPC_METHOD, compressionResult.type(), ping.length, compressionResult.data(),this, timeoutSeconds);
    }

    private Pong decodeReply(ProtobufResponse response) throws InvalidProtocolBufferException {
        CompressionType compression = CompressionType.valueOf(response.compression());
        byte[] responseBytes = resourcePool.compressor().decompress(response.compressedPayload(), compression, response.uncompressedSize());
        var reply = SearchProtocol.MonitorReply.parseFrom(responseBytes);

        if (reply.getDistributionKey() != node.key()) {
            return new Pong(ErrorMessage.createBackendCommunicationError("Expected pong from node id " + node.key() +
                                                                         ", response is from id " + reply.getDistributionKey()));
        } else if (!reply.getOnline()) {
            return new Pong(ErrorMessage.createBackendCommunicationError("Node id " + node.key() + " reports being offline"));
        } else {
            return new Pong(reply.getActiveDocs(), reply.getIsBlockingWrites());
        }
    }

    @Override
    public void receive(ResponseOrError<ProtobufResponse> response) {
        if (node.isLastReceivedPong(pingSequenceId)) {
            handler.get().handle(toPong(response));
        } else {
            //TODO Reduce to debug or remove once we have enumerated what happens here.
            log.info("Pong " + pingSequenceId + " received too late, latest is " + node.getLastReceivedPongId());
        }
    }
}
