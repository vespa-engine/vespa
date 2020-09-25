// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;

import com.yahoo.config.provision.TenantName;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link DeployLogger} which persists messages as a {@link Slime} tree, and holds a tenant and application name.
 * 
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class DeployHandlerLogger implements DeployLogger {

    private static final Logger log = Logger.getLogger(DeployHandlerLogger.class.getName());

    private final String prefix;
    private final boolean verbose;
    private final Slime slime;
    private final Cursor logroot;

    private DeployHandlerLogger(String prefix, boolean verbose) {
        this.prefix = prefix;
        this.verbose = verbose;
        this.slime = new Slime();
        this.logroot = slime.setObject().setArray("log");
    }

    @Override
    public void log(Level level, String message) {
        if ((level == Level.FINE || level == LogLevel.DEBUG || level == LogLevel.SPAM) && !verbose)
            return;

        String fullMsg = prefix + message;
        Cursor entry = logroot.addObject();
        entry.setLong("time", System.currentTimeMillis());
        entry.setString("level", level.getName());
        entry.setString("message", fullMsg);
        // Also tee to a normal log, Vespa log for example, but use level fine 
        log.log(Level.FINE, fullMsg);
    }

    public Slime slime() {
        return slime;
    }

    public static DeployHandlerLogger forApplication(ApplicationId app, boolean verbose) {
        return new DeployHandlerLogger(TenantRepository.logPre(app), verbose);
    }

    public static DeployHandlerLogger forTenant(TenantName tenantName, boolean verbose) {
        return new DeployHandlerLogger(TenantRepository.logPre(tenantName), verbose);
    }

    public static DeployHandlerLogger forPrepareParams(PrepareParams prepareParams) {
        return forApplication(prepareParams.getApplicationId(), prepareParams.isVerbose());
    }
}
