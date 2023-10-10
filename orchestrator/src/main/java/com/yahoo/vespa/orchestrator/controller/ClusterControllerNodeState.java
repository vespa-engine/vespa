// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * This denotes the different states passable through the set-node-state API against the ClusterController.
 * In the cluster controller, it maps to com.yahoo.vdslib.state.State. Consider using that instead (however
 * that class is already fairly complicated, and may perhaps best be screened from JSON annotations - the only
 * thing we need is the enum &lt; - &gt; String conversions).
 *
 * @author hakonhall
 */
public enum ClusterControllerNodeState {
    MAINTENANCE("maintenance"),
    UP("up"),
    DOWN("down");

    private final String wireName;

    ClusterControllerNodeState(final String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String getWireName() {
        return wireName;
    }
}
