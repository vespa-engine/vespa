// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.requests;

import com.yahoo.time.TimeBudget;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidContentException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitState;

import java.util.Map;

public interface SetUnitStateRequest extends UnitRequest {

    Map<String, UnitState> getNewState();

    enum Condition {
        FORCE(1), // Don't check for any condition before setting unit state
        SAFE(2); // Only set condition if it is deemed safe (e.g. redundancy is still ok during upgrade)

        public final int value;

        Condition(int value) {
            this.value = value;
        }

        public static Condition fromString(String value) throws InvalidContentException {
            try {
                return Condition.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidContentException(String.format("Invalid value for condition: '%s', expected one of 'force', 'safe'", value));
            }
        }
    }
    Condition getCondition();

    enum ResponseWait {
        /**
         * Wait for state change to be ACKed by cluster. Default unless request
         * explicitly specifies otherwise.
         */
        WAIT_UNTIL_CLUSTER_ACKED("wait-until-cluster-acked"),
        /**
         * Return without waiting for state change to be ACKed by cluster.
         */
        NO_WAIT("no-wait");

        private final String name;

        ResponseWait(String name) { this.name = name; }

        public String getName() { return this.name; }

        @Override
        public String toString() { return name; }

        public static ResponseWait fromString(String value) throws InvalidContentException {
            if (value.equalsIgnoreCase(WAIT_UNTIL_CLUSTER_ACKED.name)) {
                return WAIT_UNTIL_CLUSTER_ACKED;
            } else if (value.equalsIgnoreCase(NO_WAIT.name)) {
                return NO_WAIT;
            }
            throw new InvalidContentException(String.format("Invalid value for response-wait: '%s', expected one of '%s', '%s'",
                    value, WAIT_UNTIL_CLUSTER_ACKED.name, NO_WAIT.name));
        }
    }
    ResponseWait getResponseWait();

    TimeBudget timeBudget();

    /** A probe request is a non-committal request to see if an identical (but non-probe) request would have succeeded. */
    boolean isProbe();
}
