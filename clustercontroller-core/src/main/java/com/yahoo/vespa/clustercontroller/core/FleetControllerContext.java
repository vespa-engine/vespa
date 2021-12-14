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

    default void log(Logger logger, Level level, String format, Object first, Object... rest) {
        log(logger, level, () -> {
            var args = new Object[1 + rest.length];
            args[0] = first;
            System.arraycopy(rest, 0, args, 1, rest.length);
            return String.format(format, args);
        });
    }
}
