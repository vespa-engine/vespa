// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
 * Controller database implementation that writes to both a ControllerDb and a CuratorDb.
 *
 * @author mpolden
 */
// TODO: Remove this and ControllerDb and use only CuratorDb
public class ControllerDbProxy implements ControllerDb {

    private final ControllerDb db;
    private final CuratorDb curator;

    public ControllerDbProxy(ControllerDb db, CuratorDb curator) {
        this.db = db;
        this.curator = curator;
    }

    @Override
    public void createTenant(UserTenant tenant) {
        db.createTenant(tenant);
        curator.writeTenant(tenant);
    }

    @Override
    public void createTenant(AthenzTenant tenant) {
        db.createTenant(tenant);
        curator.writeTenant(tenant);
    }

    @Override
    public void updateTenant(AthenzTenant tenant) throws PersistenceException {
        db.updateTenant(tenant);
        curator.writeTenant(tenant);
    }

    @Override
    public void deleteTenant(TenantName name) throws PersistenceException {
        db.deleteTenant(name);
        curator.removeTenant(name);
    }

    @Override
    public Optional<Tenant> getTenant(TenantName name) throws PersistenceException {
        return db.getTenant(name);
    }

    @Override
    public Optional<AthenzTenant> getAthenzTenant(TenantName name) throws PersistenceException {
        return db.getAthenzTenant(name);
    }

    @Override
    public List<Tenant> listTenants() {
        return db.listTenants();
    }

    @Override
    public void store(Application application) {
        db.store(application);
        curator.writeApplication(application);
    }

    @Override
    public void deleteApplication(ApplicationId applicationId) {
        db.deleteApplication(applicationId);
        curator.removeApplication(applicationId);
    }

    @Override
    public Optional<Application> getApplication(ApplicationId applicationId) {
        return db.getApplication(applicationId);
    }

    @Override
    public List<Application> listApplications() {
        return db.listApplications();
    }

    @Override
    public List<Application> listApplications(TenantName name) {
        return db.listApplications(name);
    }
}
