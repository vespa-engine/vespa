// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author hakon
 */
public class FleetControllerContextImpl implements FleetControllerContext {
    private final FleetControllerId id;

    public FleetControllerContextImpl(FleetControllerOptions options) {
        this(FleetControllerId.fromOptions(options));
    }

    public FleetControllerContextImpl(FleetControllerId id) {
        this.id = id;
    }

    @Override
    public FleetControllerId id() { return id; }

    @Override
    public void log(Logger logger, Level level, String message, Throwable t) {
        logger.log(level, withLogPrefix(message), t);
    }

    @Override
    public void log(Logger logger, Level level, Supplier<String> message) {
        logger.log(level, () -> withLogPrefix(message.get()));
    }

    protected String withLogPrefix(String message) { return "Cluster '" + id.clusterName() + "': " + message; }
}
