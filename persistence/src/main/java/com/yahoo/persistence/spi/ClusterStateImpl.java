// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi;

import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;

import java.nio.ByteBuffer;
import java.text.ParseException;

/**
 * Implementation of the cluster state interface for deserialization from RPC.
 */
public class ClusterStateImpl implements com.yahoo.persistence.spi.ClusterState {
    com.yahoo.vdslib.state.ClusterState clusterState;
    short clusterIndex;
    Distribution distribution;

    public ClusterStateImpl(byte[] serialized) throws ParseException {
        ByteBuffer buf = ByteBuffer.wrap(serialized);

        int clusterStateLength = buf.getInt();
        byte[] clusterState = new byte[clusterStateLength];
        buf.get(clusterState);

        clusterIndex = buf.getShort();

        int distributionLength = buf.getInt();
        byte[] distribution = new byte[distributionLength];
        buf.get(distribution);

        this.clusterState = new com.yahoo.vdslib.state.ClusterState(new String(clusterState));
        this.distribution = new Distribution("raw:" + new String(distribution));
    }

    @Override
    public boolean shouldBeReady(Bucket bucket) {
        return true;
    }

    @Override
    public boolean clusterUp() {
        return clusterState != null && clusterState.getClusterState().oneOf("u");
    }

    @Override
    public boolean nodeUp() {
        return !clusterUp() && clusterState.getNodeState(new Node(NodeType.STORAGE, clusterIndex)).getState().oneOf("uir");
    }
}
