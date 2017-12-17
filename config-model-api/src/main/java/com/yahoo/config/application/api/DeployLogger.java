// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import java.util.logging.Level;

/**
 * Used during application deployment to persist and propagate messages to end user
 * 
 * @author lulf
 * @since 5.1
 */
public interface DeployLogger {

    void log(Level level, String message);

}
