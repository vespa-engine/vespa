// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.testutils;

import ai.vespa.validation.Validation;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.core.DummyVdsNode;
import com.yahoo.vespa.clustercontroller.core.FleetController;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public interface Waiter {

    interface DataRetriever {
        Object getMonitor();
        FleetController getFleetController();
        List<DummyVdsNode> getDummyNodes();
        Duration getTimeout();
    }

    ClusterState waitForState(String state) throws Exception;
    ClusterState waitForStateInSpace(String space, String state) throws Exception;
    ClusterState waitForStateInAllSpaces(String state) throws Exception;
    ClusterState waitForState(String state, Duration timeoutMS) throws Exception;
    ClusterState waitForStableSystem() throws Exception;
    ClusterState waitForStableSystem(int nodeCount) throws Exception;
    ClusterState waitForInitProgressPassed(Node n, double progress);
    ClusterState waitForClusterStateIncludingNodesWithMinUsedBits(int bitcount, int nodecount);
    void wait(WaitCondition c, WaitTask wt, Duration timeout);

    class Impl implements Waiter {

        private static final Logger log = Logger.getLogger(Impl.class.getName());
        private final DataRetriever data;

        public Impl(DataRetriever data) {
            this.data = data;
        }

        // TODO refactor
        private ClusterState waitForState(String state, Duration timeout, boolean checkAllSpaces, Set<String> checkSpaces) {
                LinkedList<DummyVdsNode> nodesToCheck = new LinkedList<>();
            for(DummyVdsNode node : data.getDummyNodes()) {
                if (node.isConnected()) nodesToCheck.add(node);
            }
            WaitCondition.StateWait swc = new WaitCondition.RegexStateMatcher(state, data.getFleetController(), data.getMonitor())
                    .includeNotifyingNodes(nodesToCheck)
                    .checkAllSpaces(checkAllSpaces)
                    .checkSpaceSubset(checkSpaces);
            wait(swc, new WaitTask.StateResender(data.getFleetController()), timeout);
            return swc.getCurrentState();
        }

        public ClusterState waitForState(String state) {
            return waitForState(state, data.getTimeout());
        }
        public ClusterState waitForStateInAllSpaces(String state) {
            return waitForState(state, data.getTimeout(), true, Collections.emptySet());
        }
        public ClusterState waitForStateInSpace(String space, String state) {
            return waitForState(state, data.getTimeout(), false, Collections.singleton(space));
        }
        public ClusterState waitForState(String state, Duration timeoutMS) {
            return waitForState(state, timeoutMS, false, Collections.emptySet());
        }
        public ClusterState waitForStableSystem() {
            return waitForStableSystem(data.getDummyNodes().size() / 2);
        }
        public ClusterState waitForStableSystem(int nodeCount) {
            WaitCondition.StateWait swc = new WaitCondition.RegexStateMatcher("version:\\d+ distributor:"+nodeCount+" storage:"+nodeCount, data.getFleetController(), data.getMonitor()).includeNotifyingNodes(data.getDummyNodes());
            wait(swc, new WaitTask.StateResender(data.getFleetController()), data.getTimeout());
            return swc.getCurrentState();
        }
        public ClusterState waitForInitProgressPassed(Node n, double progress) {
            WaitCondition.StateWait swc = new WaitCondition.InitProgressPassedMatcher(n, progress, data.getFleetController(), data.getMonitor());
            wait(swc, new WaitTask.StateResender(data.getFleetController()), data.getTimeout());
            return swc.getCurrentState();
        }
        public ClusterState waitForClusterStateIncludingNodesWithMinUsedBits(int bitcount, int nodecount) {
            WaitCondition.StateWait swc = new WaitCondition.MinUsedBitsMatcher(bitcount, nodecount, data.getFleetController(), data.getMonitor());
            wait(swc, new WaitTask.StateResender(data.getFleetController()), data.getTimeout());
            return swc.getCurrentState();
        }

        public final void wait(WaitCondition c, WaitTask wt, Duration timeout) {
            Objects.requireNonNull(wt, "wait task cannot be null");
            Validation.requireAtLeast(timeout.toMillis(), "timeout must be positive", 1L);

            log.log(Level.INFO, "Waiting for " + c + " with wait task " + wt);
            Instant endTime = Instant.now().plus(timeout);
            String lastReason = null;
            while (true) {
                synchronized (data.getMonitor()) {
                    String reason = c.isConditionMet();
                    if (reason == null) {
                        log.log(Level.INFO, "Condition met. Returning");
                        return;
                    }
                    if (lastReason == null || !lastReason.equals(reason)) {
                        log.log(Level.INFO, "Wait condition not met: " + reason);
                        lastReason = reason;
                    }
                    try {
                        boolean allowWait = true;
                        if (wt.performWaitTask()) {
                            data.getMonitor().notifyAll();
                            allowWait = false;
                        }
                        Duration timeLeft = Duration.between(Instant.now(), endTime);
                        if (timeLeft.isNegative() || timeLeft.isZero())
                            throw new IllegalStateException("Timed out waiting max " + timeout + " ms for " + c + "\n  with wait task " + wt + ",\n  reason: " + reason);
                        if (allowWait)
                            data.getMonitor().wait(Math.min(wt.getWaitTaskFrequencyInMillis(), timeLeft.toMillis()));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

}
