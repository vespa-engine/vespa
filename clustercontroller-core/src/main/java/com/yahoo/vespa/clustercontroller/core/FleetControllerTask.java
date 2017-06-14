// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Represents a task that the fleet controller should perform.
 */
public interface FleetControllerTask {
    public void execute(FleetController fleetController);
}
