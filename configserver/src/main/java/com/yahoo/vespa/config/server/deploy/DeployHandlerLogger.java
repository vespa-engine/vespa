// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
 * A {@link DeployLogger} which stores messages in a {@link Slime} tree, and holds a tenant and application name.
 * 
 * @author Ulf Lilleengen
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
    @SuppressWarnings("deprecation")
    public void log(Level level, String message) {
        if (level.intValue() <= LogLevel.DEBUG.intValue() && !verbose)
            return;

        logJson(level, message);
        // Also tee to a normal log, Vespa log for example, but use level fine 
        log.log(Level.FINE, () -> prefix + message);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void logApplicationPackage(Level level, String message) {
        if (level.intValue() <= LogLevel.DEBUG.intValue() && !verbose)
            return;

        Cursor entry = logJson(level, message);
        entry.setBool("applicationPackage", true);
        // Also tee to a normal log, Vespa log for example, but use level fine
        log.log(Level.FINE, () -> prefix + message);
    }

    private Cursor logJson(Level level, String message) {
        Cursor entry = logroot.addObject();
        entry.setLong("time", System.currentTimeMillis());
        entry.setString("level", level.getName());
        entry.setString("message", message);
        return entry;
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
