// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpEdgeMaintenanceTransitionConstraintTest {

    private static UpEdgeMaintenanceTransitionConstraint makeChecker(String state) {
        return UpEdgeMaintenanceTransitionConstraint.forPreviouslyPublishedState(ClusterState.stateFromString(state));
    }

    private static boolean nodeMayTransitionToMaintenanceInState(int contentNodeIndex, String state) {
        UpEdgeMaintenanceTransitionConstraint checker = makeChecker(state);
        return checker.maintenanceTransitionAllowed(contentNodeIndex);
    }

    @Test
    public void transition_allowed_when_previous_state_is_down() {
        assertTrue(nodeMayTransitionToMaintenanceInState(1, "distributor:5 storage:5 .1.s:d"));
    }

    @Test
    public void transition_allowed_when_previous_state_is_maintenance() {
        assertTrue(nodeMayTransitionToMaintenanceInState(1, "distributor:5 storage:5 .1.s:m"));
    }

    @Test
    public void transition_not_allowed_when_previous_state_is_up() {
        assertFalse(nodeMayTransitionToMaintenanceInState(0, "distributor:5 storage:5"));
    }

    @Test
    public void transition_not_allowed_when_previous_state_is_initializing() {
        assertFalse(nodeMayTransitionToMaintenanceInState(0, "distributor:5 storage:5 .0.s:i"));
    }

    @Test
    public void transition_not_allowed_when_previous_state_is_retired() {
        assertFalse(nodeMayTransitionToMaintenanceInState(0, "distributor:5 storage:5 .0.s:r"));
    }

}
