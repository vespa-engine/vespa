// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.searchcluster.Node;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RpcPingTest {
    @Test
    @SuppressWarnings("unchecked")
    void onlyAcceptedOnlineRepliesAdvertiseCorrelatedQuerySupport() {
        var node = new Node("test", 7, "seven", 0, false);
        ClusterMonitor<Node> monitor = mock(ClusterMonitor.class);
        var compressor = new Compressor(CompressionType.NONE, 0, 1, 0);
        var reply = SearchProtocol.MonitorReply.newBuilder().setDistributionKey(7).setOnline(true);
        assertFalse(node.supportsCorrelatedQueries());
        for (boolean supported : new boolean[]{false, true, false}) {
            var ping = new RpcPing(node, monitor, null, pong -> assertFalse(pong.badResponse()), compressor);
            byte[] bytes = reply.setSupportsCorrelatedQueries(supported).build().toByteArray();
            ping.receive(Client.ResponseOrError.fromResponse(new Client.ProtobufResponse(CompressionType.NONE.getCode(), bytes.length, bytes)));
            assertEquals(supported, node.supportsCorrelatedQueries());
        }
        var stale = new RpcPing(node, monitor, null, pong -> fail("Stale pong must be ignored"), compressor);
        var current = new RpcPing(node, monitor, null, pong -> assertFalse(pong.badResponse()), compressor);
        byte[] oldReply = reply.setSupportsCorrelatedQueries(false).build().toByteArray();
        byte[] newReply = reply.setSupportsCorrelatedQueries(true).build().toByteArray();
        current.receive(Client.ResponseOrError.fromResponse(new Client.ProtobufResponse(CompressionType.NONE.getCode(), newReply.length, newReply)));
        stale.receive(Client.ResponseOrError.fromResponse(new Client.ProtobufResponse(CompressionType.NONE.getCode(), oldReply.length, oldReply)));
        assertTrue(node.supportsCorrelatedQueries());
        var offline = new RpcPing(node, monitor, null, pong -> assertTrue(pong.badResponse()), compressor);
        byte[] offlineReply = reply.setOnline(false).build().toByteArray();
        offline.receive(Client.ResponseOrError.fromResponse(new Client.ProtobufResponse(CompressionType.NONE.getCode(), offlineReply.length, offlineReply)));
        assertFalse(node.supportsCorrelatedQueries());
        node.setSupportsCorrelatedQueries(true);
        node.setWorking(false);
        assertFalse(node.supportsCorrelatedQueries());
    }
    private static Client.ResponseOrError<Client.ProtobufResponse> reply(boolean supported) {
        byte[] bytes = SearchProtocol.MonitorReply.newBuilder().setDistributionKey(7).setOnline(true)
                                   .setSupportsCorrelatedQueries(supported).build().toByteArray();
        return Client.ResponseOrError.fromResponse(new Client.ProtobufResponse(CompressionType.NONE.getCode(), bytes.length, bytes));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSlowOlderReplyCannotOverwriteANewerCapability() throws Exception {
        var node = new Node("test", 7, "seven", 0, false);
        ClusterMonitor<Node> monitor = mock(ClusterMonitor.class);
        var decoding = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        var slowCompressor = new Compressor(CompressionType.NONE, 0, 1, 0) {
            @Override
            public byte[] decompress(byte[] payload, CompressionType type, int size) {
                decoding.countDown();
                try {
                    if (!resume.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out resuming reply");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return super.decompress(payload, type, size);
            }
        };
        var older = new RpcPing(node, monitor, null, pong -> fail("Older reply must be ignored"), slowCompressor);
        var current = new RpcPing(node, monitor, null, pong -> assertFalse(pong.badResponse()),
                                  new Compressor(CompressionType.NONE, 0, 1, 0));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var pending = executor.submit(() -> older.receive(reply(true)));
            assertTrue(decoding.await(5, TimeUnit.SECONDS));
            current.receive(reply(false));
            resume.countDown();
            pending.get(5, TimeUnit.SECONDS);
            assertFalse(node.supportsCorrelatedQueries());
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void failuresClearCapabilitiesAndObsoleteSendFailuresAreIgnored() {
        var node = new Node("test", 7, "seven", 0, false);
        ClusterMonitor<Node> monitor = mock(ClusterMonitor.class);
        var compressor = new Compressor(CompressionType.NONE, 0, 1, 0);
        var stale = new RpcPing(node, monitor, null, pong -> fail("Obsolete send failure must be ignored"), compressor);
        var current = new RpcPing(node, monitor, null, pong -> assertFalse(pong.badResponse()), compressor);
        current.receive(reply(true));
        stale.ping(); // The missing pool fails before sending.
        assertTrue(node.supportsCorrelatedQueries());
        for (int scenario = 0; scenario < 4; ++scenario) {
            node.setSupportsCorrelatedQueries(true);
            var failed = new RpcPing(node, monitor, null, pong -> assertTrue(pong.badResponse()), compressor);
            switch (scenario) {
                case 0 -> failed.receive(null);
                case 1 -> failed.receive(Client.ResponseOrError.fromError("connection lost"));
                case 2 -> failed.receive(Client.ResponseOrError.fromResponse(
                        new Client.ProtobufResponse(CompressionType.NONE.getCode(), 1, new byte[]{(byte) 0xff})));
                case 3 -> failed.ping();
            }
            assertFalse(node.supportsCorrelatedQueries());
        }
    }

}
