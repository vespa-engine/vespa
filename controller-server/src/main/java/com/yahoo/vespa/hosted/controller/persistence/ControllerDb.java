// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.base.Joiner;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.identifiers.Identifier;
import com.yahoo.vespa.hosted.controller.api.identifiers.RotationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Used to store the permanent data of the controller.
 * 
 * @author Stian Kristoffersen
 * @author bratseth
 */
public abstract class ControllerDb {

    // --------- Tenants

    public abstract void createTenant(Tenant tenant);

    public abstract void updateTenant(Tenant tenant) throws PersistenceException;

    public abstract void deleteTenant(TenantId tenantId) throws PersistenceException;

    public abstract Optional<Tenant> getTenant(TenantId tenantId) throws PersistenceException;

    public abstract List<Tenant> listTenants();

    // --------- Applications

    // ONLY call this from ApplicationController.store()
    public abstract void store(Application application);

    public abstract void deleteApplication(ApplicationId applicationId);

    public abstract Optional<Application> getApplication(ApplicationId applicationId);

    /** Returns all applications */
    public abstract List<Application> listApplications();

    /** Returns all applications of a tenant */
    public abstract List<Application> listApplications(TenantId tenantId);

    // --------- Rotations
    
    public abstract Set<RotationId> getRotations();

    public abstract Set<RotationId> getRotations(ApplicationId applicationId);

    public abstract boolean assignRotation(RotationId rotationId, ApplicationId applicationId);

    public abstract Set<RotationId> deleteRotations(ApplicationId applicationId);

    /** Returns the given elements joined by dot "." */
    protected String path(Identifier... elements) {
        return Joiner.on(".").join(elements);
    }

    protected String path(String... elements) {
        return Joiner.on(".").join(elements);
    }
    
    protected String path(ApplicationId applicationId) {
        return applicationId.tenant().value() + "." + applicationId.application().value() + "." + applicationId.instance().value();
    }

}
