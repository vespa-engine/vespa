// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.testutils;

import com.yahoo.log.LogLevel;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.core.DummyVdsNode;
import com.yahoo.vespa.clustercontroller.core.FleetController;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public interface Waiter {

    interface DataRetriever {
        Object getMonitor();
        FleetController getFleetController();
        List<DummyVdsNode> getDummyNodes();
        int getTimeoutMS();
    }

    ClusterState waitForState(String state) throws Exception;
    ClusterState waitForStateInSpace(String space, String state) throws Exception;
    ClusterState waitForStateInAllSpaces(String state) throws Exception;
    ClusterState waitForState(String state, int timeoutMS) throws Exception;
    ClusterState waitForStableSystem() throws Exception;
    ClusterState waitForStableSystem(int nodeCount) throws Exception;
    ClusterState waitForInitProgressPassed(Node n, double progress);
    ClusterState waitForClusterStateIncludingNodesWithMinUsedBits(int bitcount, int nodecount);
    void wait(WaitCondition c, WaitTask wt, int timeoutMS);

    class Impl implements Waiter {

        private static final Logger log = Logger.getLogger(Impl.class.getName());
        private final DataRetriever data;

        public Impl(DataRetriever data) {
            this.data = data;
        }

        // TODO refactor
        private ClusterState waitForState(String state, int timeoutMS, boolean checkAllSpaces, Set<String> checkSpaces) {
            LinkedList<DummyVdsNode> nodesToCheck = new LinkedList<>();
            for(DummyVdsNode node : data.getDummyNodes()) {
                if (node.isConnected()) nodesToCheck.add(node);
            }
            WaitCondition.StateWait swc = new WaitCondition.RegexStateMatcher(
                    state, data.getFleetController(), data.getMonitor())
                    .includeNotifyingNodes(nodesToCheck)
                    .checkAllSpaces(checkAllSpaces)
                    .checkSpaceSubset(checkSpaces);
            wait(swc, new WaitTask.StateResender(data.getFleetController()), timeoutMS);
            return swc.getCurrentState();
        }

        public ClusterState waitForState(String state) throws Exception {
            return waitForState(state, data.getTimeoutMS());
        }
        public ClusterState waitForStateInAllSpaces(String state) {
            return waitForState(state, data.getTimeoutMS(), true, Collections.emptySet());
        }
        public ClusterState waitForStateInSpace(String space, String state) {
            return waitForState(state, data.getTimeoutMS(), false, Collections.singleton(space));
        }
        public ClusterState waitForState(String state, int timeoutMS) {
            return waitForState(state, timeoutMS, false, Collections.emptySet());
        }
        public ClusterState waitForStableSystem() throws Exception {
            return waitForStableSystem(data.getDummyNodes().size() / 2);
        }
        public ClusterState waitForStableSystem(int nodeCount) throws Exception {
            WaitCondition.StateWait swc = new WaitCondition.RegexStateMatcher("version:\\d+ distributor:"+nodeCount+" storage:"+nodeCount, data.getFleetController(), data.getMonitor()).includeNotifyingNodes(data.getDummyNodes());
            wait(swc, new WaitTask.StateResender(data.getFleetController()), data.getTimeoutMS());
            return swc.getCurrentState();
        }
        public ClusterState waitForInitProgressPassed(Node n, double progress) {
            WaitCondition.StateWait swc = new WaitCondition.InitProgressPassedMatcher(n, progress, data.getFleetController(), data.getMonitor());
            wait(swc, new WaitTask.StateResender(data.getFleetController()), data.getTimeoutMS());
            return swc.getCurrentState();
        }
        public ClusterState waitForClusterStateIncludingNodesWithMinUsedBits(int bitcount, int nodecount) {
            WaitCondition.StateWait swc = new WaitCondition.MinUsedBitsMatcher(bitcount, nodecount, data.getFleetController(), data.getMonitor());
            wait(swc, new WaitTask.StateResender(data.getFleetController()), data.getTimeoutMS());
            return swc.getCurrentState();
        }

        public final void wait(WaitCondition c, WaitTask wt, int timeoutMS) {
            log.log(LogLevel.INFO, "Waiting for " + c + (wt == null ? "" : " with wait task " + wt));
            final long startTime = System.currentTimeMillis();
            final long endTime = startTime + timeoutMS;
            String lastReason = null;
            while (true) {
                synchronized (data.getMonitor()) {
                    String reason = c.isConditionMet();
                    if (reason == null) {
                        log.log(LogLevel.INFO, "Condition met. Returning");
                        return;
                    }
                    if (lastReason == null || !lastReason.equals(reason)) {
                        log.log(LogLevel.INFO, "Wait condition not met: " + reason);
                        lastReason = reason;
                    }
                    try {
                        boolean allowWait = true;
                        if (wt != null) {
                            if (wt.performWaitTask()) {
                                data.getMonitor().notifyAll();
                                allowWait = false;
                            }
                        }
                        final long timeLeft = endTime - System.currentTimeMillis();
                        if (timeLeft <= 0) {
                            throw new IllegalStateException("Timed out waiting max " + timeoutMS + " ms for " + c + (wt == null ? "" : "\n  with wait task " + wt) + ",\n  reason: " + reason);
                        }
                        if (allowWait) data.getMonitor().wait(wt == null ? WaitTask.defaultTaskFrequencyMillis : Math.min(wt.getWaitTaskFrequencyInMillis(), timeLeft));
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

}
