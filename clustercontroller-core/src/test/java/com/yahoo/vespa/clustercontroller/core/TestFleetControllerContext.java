// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * @author hakon
 */
public class TestFleetControllerContext extends FleetControllerContextImpl {
    public TestFleetControllerContext(FleetControllerOptions options) {
        super(options);
    }

    public TestFleetControllerContext(FleetControllerId id) {
        super(id);
    }

    @Override
    protected String withLogPrefix(String message) {
        // Include fleet controller index in prefix in tests, since many may be running
        return id() + ": " + message;
    }
}
