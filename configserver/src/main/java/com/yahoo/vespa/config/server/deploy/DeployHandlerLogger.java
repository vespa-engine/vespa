// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link DeployLogger} which persists messages as a {@link Slime} tree, and holds a tenant and application name.
 * 
 * @author lulf
 * @since 5.1
 */
public class DeployHandlerLogger implements DeployLogger {

    private static final Logger log = Logger.getLogger(DeployHandlerLogger.class.getName());

    private final Cursor logroot;
    private final boolean verbose;
    private final ApplicationId app;

    public DeployHandlerLogger(Cursor root, boolean verbose, ApplicationId app) {
        logroot = root;
        this.verbose = verbose;
        this.app = app;
    }

    @Override
    public void log(Level level, String message) {
        if ((level == LogLevel.FINE ||
             level == LogLevel.DEBUG ||
             level == LogLevel.SPAM) &&
            !verbose) {
            return;
        }
        String fullMsg = TenantRepository.logPre(app) + message;
        Cursor entry = logroot.addObject();
        entry.setLong("time", System.currentTimeMillis());
        entry.setString("level", level.getName());
        entry.setString("message", fullMsg);
        // Also tee to a normal log, Vespa log for example, but use level fine 
        log.log(LogLevel.FINE, fullMsg);
    }

}
