// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vespa.clustercontroller.core.listeners.SlobrokListener;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeListener;

import java.time.Instant;
import java.util.Optional;

public abstract class RemoteClusterControllerTask {

    public static class Context {
        public ContentCluster cluster;
        public ClusterState currentConsolidatedState;
        public ClusterStateBundle publishedClusterStateBundle;
        public MasterInterface masterInfo;
        public NodeListener nodeListener;
        public SlobrokListener slobrokListener;
    }

    private final Object monitor = new Object();
    private boolean completed = false;

    public abstract void doRemoteFleetControllerTask(Context context);

    /**
     * If the task should _not_ be considered complete before a cluster state
     * version representing the changes made by the task has been ACKed by
     * all distributors.
     *
     * Note that if a task performs a no-op state change (e.g. setting maintenance
     * mode on a node already in maintenance mode), the task may be considered complete
     * immediately if its effective changes have already been ACKed.
     */
    public boolean hasVersionAckDependency() { return false; }

    /**
     * If true, signals that a task has failed and can be immediately marked as
     * complete without waiting for a version ACK. The task implementation has
     * the responsibility of communicating any failure to the caller, and ensuring
     * that the lack of version waiting does not violate any invariants.
     */
    public boolean isFailed() { return false; }

    public enum FailureCondition {
        LEADERSHIP_LOST,
        DEADLINE_EXCEEDED
    }

    public static class Failure {
        private final FailureCondition condition;
        private final String message;

        private Failure(FailureCondition condition, String message) {
            this.condition = condition;
            this.message = message;
        }
        public static Failure of(FailureCondition condition, String message) {
            return new Failure(condition, message);
        }
        public static Failure of(FailureCondition condition) {
            return new Failure(condition, "");
        }

        public FailureCondition getCondition() {
            return condition;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     *  If the task completion has been deferred due to hasVersionAckDependency(),
     *  this method will be invoked if a failure occurs before the version has
     *  been successfully ACKed.
     *
     *  LEADERSHIP_LOST will be the failure condition if the cluster controller
     *  discovers it has lost leadership in the time between task execution and
     *  deferred completion time.
     *
     *  DEADLINE_EXCEEDED will be the failure condition if the completion has been
     *  deferred for more than a configurable amount of time.
     *
     *  This method will also be invoked if the controller is signalled to shut down
     *  before the dependent cluster version has been published.
     *
     *  The task implementation is responsible for communicating the appropriate
     *  error semantics to the caller who initially scheduled the task. If additional
     *  details are available, Failure.getMessage() will return a non-empty string.
     */
    public void handleFailure(Failure failure) {}

    public Optional<Instant> getDeadline() {
        return Optional.empty();
    }

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

    @Override
    public String toString() { return RemoteClusterControllerTask.class.getSimpleName(); }
}
