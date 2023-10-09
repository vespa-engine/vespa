// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Used during application deployment to propagate messages to the end user
 * 
 * @author Ulf Lillengen
 */
public interface DeployLogger {

    /** Log a message unrelated to the application package, e.g. internal error/status. */
    void log(Level level, String message);

    default void log(Level level, Supplier<String> message) { log(level, message.get()); }

    default void log(Level level, Supplier<String> message, Throwable throwable) { log(level, message); }

    /**
     * Log a message related to the application package. These messages should be actionable by the user, f.ex. to
     * signal usage of invalid/deprecated syntax
     */
    default void logApplicationPackage(Level level, String message) {
        log(level, message);
    }

}
