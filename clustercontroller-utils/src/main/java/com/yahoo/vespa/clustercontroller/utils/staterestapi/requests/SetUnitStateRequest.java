// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.requests;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidContentException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitState;

import java.util.Map;

public interface SetUnitStateRequest extends UnitRequest {

    Map<String, UnitState> getNewState();

    enum Condition {
        FORCE(1), // Don't check for any condition before setting unit state
        SAFE(2); // Only set condition if it is deemed safe (e.g. redundancy is still ok during upgrade)

        public final int value;

        private Condition(int value) {
            this.value = value;
        }

        public static Condition fromString(String value) throws InvalidContentException {
            try {
                return Condition.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidContentException("Invalid value for my enum Condition: " + value);
            }
        }
    }
    Condition getCondition();
}
