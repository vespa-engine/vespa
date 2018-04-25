// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.database;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;

import java.util.Map;

/**
 * This is an abstract class defining the functions needed by a database back end for the fleetcontroller.
 */
public abstract class Database {

    /** Interface used for database to send events of stuff happening during requests. */
    public interface DatabaseListener {
        public void handleZooKeeperSessionDown();
        public void handleMasterData(Map<Integer, Integer> data);
    }

    /**
     * Used when initiating shutdown to avoid zookeeper layer reporting errors afterwards.
     */
    public abstract void stopErrorReporting();

    /**
     * Close this session, and release all resources it has used.
     */
    public abstract void close();

    /**
     * @return True if the database is closed, and cannot be used anymore.
     */
    public abstract boolean isClosed();

    /**
     * Set our vote for master election. Should always be set as this is the ephemeral node used for other
     * fleetcontrollers to see that we are alive.
     *
     * @return True if request succeeded. False if not.
     */
    public abstract boolean storeMasterVote(int wantedMasterIndex) throws InterruptedException;

    /**
     * Store the latest system state version used. When the fleetcontroller makes a given version official it should
     * store the version in the database, such that if another fleetcontroller takes over as master it will use a
     * higher version system state.
     *
     * @return True if request succeeded. False if not.
     */
    public abstract boolean storeLatestSystemStateVersion(int version) throws InterruptedException;

    /**
     * Get the latest system state version used. To keep the version rising, a newly elected master will call this
     * function to see at what index it should start.
     *
     * @return The last system state version used, or null if request failed.
     */
    public abstract Integer retrieveLatestSystemStateVersion() throws InterruptedException;

    /**
     * Save our current wanted states in the database. Typically called after processing an RPC request for altering
     * a wanted state, or if the fleetcontroller decides to alter the wanted state itself.
     *
     * @return True if the request succeeded. False if not.
     */
    public abstract boolean storeWantedStates(Map<Node, NodeState> states) throws InterruptedException;

    /**
     * Read wanted states from the database and set wanted states for all nodes in the cluster accordingly.
     * This function is typically called when one take over as master fleetcontroller.
     *
     * @return True if wanted states was altered, false if not. Null if request failed.
     */
    public abstract Map<Node, NodeState> retrieveWantedStates() throws InterruptedException;

    /**
     * Store start times of distributor and service layer nodes in zookeeper.
     */
    public abstract boolean storeStartTimestamps(Map<Node, Long> timestamps) throws InterruptedException;

    /**
     * Fetch the start times of distributor and service layer nodes.
     */
    public abstract Map<Node, Long> retrieveStartTimestamps() throws InterruptedException;

    public abstract boolean storeLastPublishedStateBundle(ClusterStateBundle stateBundle) throws InterruptedException;

    public abstract ClusterStateBundle retrieveLastPublishedStateBundle() throws InterruptedException;

}
