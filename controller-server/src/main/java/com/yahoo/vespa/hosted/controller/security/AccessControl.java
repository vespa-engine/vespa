// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Instant;
import java.util.List;

/**
 * Stores permissions for tenant and application resources.
 *
 * The signatures use vague types, and the exact types is a contract between this and the
 * {@link AccessControlRequests} generating data consumed by this.
 *
 * @author jonmv
 */
public interface AccessControl {

    /**
     * Sets up access control based on the given credentials, and returns a tenant, based on the given specification.
     *
     * @param tenantSpec specification for the tenant to create
     * @param createdAt instant when the tenant was created
     * @param credentials the credentials for the entity requesting the creation
     * @param existing list of existing tenants, to check for conflicts
     * @return the created tenant, for keeping
     */
    Tenant createTenant(TenantSpec tenantSpec, Instant createdAt, Credentials credentials, List<Tenant> existing);

    /**
     * Modifies access control based on the given credentials, and returns a modified tenant, based on the given specification.
     *
     * @param tenantSpec specification for the tenant to update
     * @param credentials the credentials for the entity requesting the update
     * @param existing list of existing tenants, to check for conflicts
     * @param applications list of applications this tenant already owns
     * @return the updated tenant, for keeping
     */
    Tenant updateTenant(TenantSpec tenantSpec, Credentials credentials, List<Tenant> existing, List<Application> applications);

    /**
     * Deletes access control for the given tenant.
     *
     * @param tenant the tenant to delete
     * @param credentials the credentials for the entity requesting the deletion
     */
    void deleteTenant(TenantName tenant, Credentials credentials);

    /**
     * Sets up access control for the given application, based on the given credentials.
     *
     * @param id the ID of the application to create
     * @param credentials the credentials for the entity requesting the creation
     */
    void createApplication(TenantAndApplicationId id, Credentials credentials);

    /**
     * Deletes access control for the given tenant.
     *
     * @param id the ID of the application to delete
     * @param credentials the credentials for the entity requesting the deletion
     */
    void deleteApplication(TenantAndApplicationId id, Credentials credentials);

}
