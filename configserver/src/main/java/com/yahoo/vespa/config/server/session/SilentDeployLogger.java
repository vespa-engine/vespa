// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.yahoo.config.application.api.DeployLogger;

/**
 * The purpose of this is to mute the log messages from model and application building in {@link RemoteSession} that
 * is triggered by {@link SessionStateWatcher}, since those messages already have been emitted by the prepare
 * handler, for the same prepare operation.
 * 
 * @author vegardh
 *
 */
public class SilentDeployLogger implements DeployLogger {

    private static final Logger log = Logger.getLogger(SilentDeployLogger.class.getName());
    
    @Override
    public void log(Level level, String message) {
        log.log(Level.FINE, message);
    }

}
