// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeAddedOrRemovedListener;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;

public abstract class RemoteClusterControllerTask {

    public static class Context {
        public ContentCluster cluster;
        public ClusterState currentState;
        public MasterInterface masterInfo;
        public NodeStateOrHostInfoChangeHandler nodeStateOrHostInfoChangeHandler;
        public NodeAddedOrRemovedListener nodeAddedOrRemovedListener;
    }

    private final Object monitor = new Object();
    private boolean completed = false;

    public abstract void doRemoteFleetControllerTask(Context context);

    public boolean isCompleted() {
        synchronized (monitor) {
            return completed;
        }
    }

    /** This is called by the fleet controller. */
    public void notifyCompleted() {
        synchronized (monitor) {
            completed = true;
            monitor.notifyAll();
        }
    }

    public void waitForCompletion() {
        synchronized (monitor) {
            while (!completed) {
                try{
                    monitor.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
