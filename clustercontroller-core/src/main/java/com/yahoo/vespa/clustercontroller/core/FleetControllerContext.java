// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Context for FleetController and all instances 1:1 with the FleetController.
 *
 * @author hakon
 */
public interface FleetControllerContext {
    FleetControllerId id();

    default void log(Logger logger, Level level, String message) { log(logger, level, () -> message); }
    void log(Logger logger, Level level, String message, Throwable t);
    void log(Logger logger, Level level, Supplier<String> message);
}
