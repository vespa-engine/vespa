// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.testutils;

import com.yahoo.vespa.clustercontroller.core.FleetController;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;

/** A wait task is something that is performed once in a while while waiting for something. */
public abstract class WaitTask {
    static final int defaultTaskFrequencyMillis = 1;

    public abstract boolean performWaitTask();

    int getWaitTaskFrequencyInMillis() {
        return defaultTaskFrequencyMillis;
    }

    public static class StateResender extends WaitTask {
        public final FleetController fleetController;

        StateResender(FleetController fc) {
            fleetController = fc;
        }

        @Override
        public boolean performWaitTask() {
            boolean didWork = false;
            synchronized (fleetController.getMonitor()) {
                for (NodeInfo info : fleetController.getCluster().getNodeInfos()) {
                    if (info.getTimeForNextStateRequestAttempt() != 0) didWork = true;
                    info.setNextGetStateAttemptTime(0);
                }
            }
            return didWork;
        }

        @Override
        public String toString() {
            return "GetNodeStateResender";
        }
    }
}
