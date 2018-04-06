// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.base.Joiner;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;

import java.util.List;
import java.util.Optional;

/**
 * Used to store the permanent data of the controller.
 * 
 * @author Stian Kristoffersen
 * @author bratseth
 */
public interface ControllerDb {

    // --------- Tenants

    void createTenant(UserTenant tenant);

    void createTenant(AthenzTenant tenant);

    // TODO: Remove exception from all signatures
    void updateTenant(AthenzTenant tenant) throws PersistenceException;

    void deleteTenant(TenantName name) throws PersistenceException;

    Optional<Tenant> getTenant(TenantName name) throws PersistenceException;

    Optional<AthenzTenant> getAthenzTenant(TenantName name) throws PersistenceException;

    List<Tenant> listTenants();

    // --------- Applications

    // ONLY call this from ApplicationController.store()
    void store(Application application);

    void deleteApplication(ApplicationId applicationId);

    Optional<Application> getApplication(ApplicationId applicationId);

    /** Returns all applications */
    List<Application> listApplications();

    /** Returns all applications of a tenant */
    List<Application> listApplications(TenantName name);

    /** Returns the given elements joined by dot "." */
    default String path(TenantName... elements) {
        return Joiner.on(".").join(elements);
    }

    default String path(String... elements) {
        return Joiner.on(".").join(elements);
    }
    
    default String path(ApplicationId applicationId) {
        return applicationId.tenant().value() + "." + applicationId.application().value() + "." + applicationId.instance().value();
    }

}
