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
public interface ControllerDb {

    // --------- Tenants

    void createTenant(Tenant tenant);

    void updateTenant(Tenant tenant) throws PersistenceException;

    void deleteTenant(TenantId tenantId) throws PersistenceException;

    Optional<Tenant> getTenant(TenantId tenantId) throws PersistenceException;

    List<Tenant> listTenants();

    // --------- Applications

    // ONLY call this from ApplicationController.store()
    void store(Application application);

    void deleteApplication(ApplicationId applicationId);

    Optional<Application> getApplication(ApplicationId applicationId);

    /** Returns all applications */
    List<Application> listApplications();

    /** Returns all applications of a tenant */
    List<Application> listApplications(TenantId tenantId);

    // --------- Rotations
    
    Set<RotationId> getRotations();

    Set<RotationId> getRotations(ApplicationId applicationId);

    boolean assignRotation(RotationId rotationId, ApplicationId applicationId);

    Set<RotationId> deleteRotations(ApplicationId applicationId);

    /** Returns the given elements joined by dot "." */
    default String path(Identifier... elements) {
        return Joiner.on(".").join(elements);
    }

    default String path(String... elements) {
        return Joiner.on(".").join(elements);
    }
    
    default String path(ApplicationId applicationId) {
        return applicationId.tenant().value() + "." + applicationId.application().value() + "." + applicationId.instance().value();
    }

}
