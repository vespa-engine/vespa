// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.google.common.io.Files;
import com.yahoo.config.provision.TenantName;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.ConfigServerDB;

import java.io.File;

/*
 * Holds file system directories for a tenant
 *
 * @author tonytv
 */
public class TenantFileSystemDirs {

    private final File serverDB;
    private final TenantName tenant;

    public TenantFileSystemDirs(File dir, TenantName tenant) {
        this.serverDB = dir;
        this.tenant = tenant;
        ConfigServerDB.cr(path());
    }

    public static TenantFileSystemDirs createTestDirs(TenantName tenantName) {
        return new TenantFileSystemDirs(Files.createTempDir(), tenantName);
    }

    public File path() {
        return new File(serverDB, Path.fromString("tenants").append(tenant.value()).append("sessions").getRelative());
    }

    public File getUserApplicationDir(long generation) {
        return new File(path(), String.valueOf(generation));
    }

    public String getPath() {
        return serverDB.getPath();
    }

    public void delete() {
        IOUtils.recursiveDeleteDir(new File(serverDB, Path.fromString("tenants").append(tenant.value()).getRelative()));
    }
}
