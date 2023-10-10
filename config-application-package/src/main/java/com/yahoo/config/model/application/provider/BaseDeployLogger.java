// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.application.api.DeployLogger;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logs to a normal {@link Logger}
 *
 * @author vegardh
 */
public final class BaseDeployLogger implements DeployLogger {

    private static final Logger log = Logger.getLogger("DeployLogger");
    
    @Override
    public final void log(Level level, String message) {
        log.log(level, message);
    }

}
