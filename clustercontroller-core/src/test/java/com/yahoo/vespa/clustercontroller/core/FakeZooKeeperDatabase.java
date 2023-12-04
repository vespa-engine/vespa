// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vespa.clustercontroller.core.database.Database;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseFactory;

import java.util.Map;
import java.util.TreeMap;

/**
 * Memory-backed fake DB implementation that tries to mirror the semantics of the
 * (synchronous) ZooKeeper DB implementation. By itself this fake acts as if a quorum
 * with a _single_, local ZK instance has been configured. This DB instance cannot be
 * used across multiple cluster controller instances.
 *
 * Threading note: we expect all invocations on this instance to happen from the
 * main cluster controller thread (i.e. "as-if" single threaded), but we wrap everything
 * in a mutex to stay on the safe side since this isn't explicitly documented as
 * part of the API,
 */
public class FakeZooKeeperDatabase extends Database {

    public static class Factory implements DatabaseFactory {
        private final FleetControllerContext context;
        public Factory(FleetControllerContext context) {
            this.context = context;
        }
        @Override
        public Database create(Params params) {
            return new FakeZooKeeperDatabase(context, params.listener);
        }
    }

    private final FleetControllerContext context;
    private final Database.DatabaseListener listener;

    private final Object mutex = new Object();
    private boolean closed = false;
    private Integer persistedLatestStateVersion = null;
    private Map<Integer, Integer> persistedLeaderVotes = new TreeMap<>();
    private Map<Node, NodeState> persistedWantedStates = new TreeMap<>();
    private Map<Node, Long> persistedStartTimestamps = new TreeMap<>();
    private ClusterStateBundle persistedBundle = ClusterStateBundle.ofBaselineOnly(AnnotatedClusterState.emptyState());

    public FakeZooKeeperDatabase(FleetControllerContext context, DatabaseListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @Override
    public void close() {
        synchronized (mutex) {
            closed = true;
        }
    }

    @Override
    public boolean isClosed() {
        synchronized (mutex) {
            return closed;
        }
    }

    @Override
    public boolean storeMasterVote(int voteForNode) {
        Map<Integer, Integer> voteState;
        synchronized (mutex) {
            persistedLeaderVotes.put(context.id().index(), voteForNode);
            voteState = Map.copyOf(persistedLeaderVotes);
        }
        listener.handleMasterData(voteState);
        return true;
    }

    @Override
    public boolean storeLatestSystemStateVersion(int version) {
        synchronized (mutex) {
            persistedLatestStateVersion = version;
            return true;
        }
    }

    @Override
    public Integer retrieveLatestSystemStateVersion() {
        synchronized (mutex) {
            return persistedLatestStateVersion;
        }
    }

    @Override
    public boolean storeWantedStates(Map<Node, NodeState> states) {
        synchronized (mutex) {
            persistedWantedStates = Map.copyOf(states);
        }
        return true;
    }

    @Override
    public Map<Node, NodeState> retrieveWantedStates() {
        synchronized (mutex) {
            return Map.copyOf(persistedWantedStates);
        }
    }

    @Override
    public boolean storeStartTimestamps(Map<Node, Long> timestamps) {
        synchronized (mutex) {
            persistedStartTimestamps = Map.copyOf(timestamps);
            return true;
        }
    }

    @Override
    public Map<Node, Long> retrieveStartTimestamps() {
        synchronized (mutex) {
            return Map.copyOf(persistedStartTimestamps);
        }
    }

    @Override
    public boolean storeLastPublishedStateBundle(ClusterStateBundle stateBundle) {
        synchronized (mutex) {
            persistedBundle = stateBundle;
            return true;
        }
    }

    @Override
    public ClusterStateBundle retrieveLastPublishedStateBundle() {
        synchronized (mutex) {
            return persistedBundle;
        }
    }
}
