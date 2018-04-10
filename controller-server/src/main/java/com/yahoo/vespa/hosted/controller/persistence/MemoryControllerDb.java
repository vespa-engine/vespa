// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;

import java.util.List;
import java.util.Optional;

/**
 * A controller db implementation backed by a mock curator. Useful for testing.
 * 
 * @author Stian Kristoffersen
 */
public class MemoryControllerDb implements ControllerDb {

    private final MockCuratorDb curator = new MockCuratorDb();

    @Override
    public void createTenant(UserTenant tenant) {
        curator.writeTenant(tenant);
    }

    @Override
    public void createTenant(AthenzTenant tenant) {
        curator.writeTenant(tenant);
    }

    @Override
    public void updateTenant(AthenzTenant tenant) {
        curator.writeTenant(tenant);
    }

    @Override
    public void deleteTenant(TenantName name) {
        curator.removeTenant(name);
    }

    @Override
    public Optional<Tenant> getTenant(TenantName name) {
        return curator.readTenant(name);
    }

    @Override
    public Optional<AthenzTenant> getAthenzTenant(TenantName name) {
        return curator.readAthenzTenant(name);
    }

    @Override
    public List<Tenant> listTenants() {
        return curator.readTenants();
    }

    @Override
    public void store(Application application) {
        curator.writeApplication(application);
    }

    @Override
    public void deleteApplication(ApplicationId applicationId) {
        curator.removeApplication(applicationId);
    }

    @Override
    public Optional<Application> getApplication(ApplicationId applicationId) {
        return curator.readApplication(applicationId);
    }

    @Override
    public List<Application> listApplications() {
        return curator.readApplications();
    }

    @Override
    public List<Application> listApplications(TenantName name) {
        return curator.readApplications(name);
    }

}
