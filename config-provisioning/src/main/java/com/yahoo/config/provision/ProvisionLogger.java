// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.logging.Level;

/**
 * Allows messages to be logged during provision which will be directed back to the party initiating the request.
 *
 * @author bratseth
 */
public interface ProvisionLogger {

    void log(Level level, String message);

}
