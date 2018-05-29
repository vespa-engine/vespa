// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.TenantName;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.ConfigServerDB;

import java.io.File;

/*
 * Holds file system directories for a tenant
 *
 * @author Tony Vaagenes
 */
public class TenantFileSystemDirs {

    private final File serverDB;
    private final TenantName tenant;

    public TenantFileSystemDirs(ConfigserverConfig configserverConfig, TenantName tenant) {
        this(new ConfigServerDB(configserverConfig).path(), tenant);
    }

    // For testing
    public TenantFileSystemDirs(File dir, TenantName tenant) {
        this.serverDB = dir;
        this.tenant = tenant;
        ConfigServerDB.createDirectory(sessionsPath());
    }

    public File sessionsPath() {
        return new File(serverDB, Path.fromString("tenants").append(tenant.value()).append("sessions").getRelative());
    }

    public File getUserApplicationDir(long generation) {
        return new File(sessionsPath(), String.valueOf(generation));
    }

    public void delete() {
        IOUtils.recursiveDeleteDir(new File(serverDB, Path.fromString("tenants").append(tenant.value()).getRelative()));
    }
}
