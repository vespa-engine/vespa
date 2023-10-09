// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.logging.Level;

/**
 * Allows messages to be logged during provision which will be directed back to the party initiating the request.
 *
 * @author bratseth
 */
public interface ProvisionLogger {

    /** Log a message unrelated to the application package, e.g. internal error/status. */
    void log(Level level, String message);

    /**
     * Log a message related to the application package. These messages should be actionable by the user, f.ex. to
     * signal usage of invalid/deprecated syntax.
     * This default implementation just forwards to {@link #log(Level, String)}
     */
    default void logApplicationPackage(Level level, String message) {
        log(level, message);
    }

}
