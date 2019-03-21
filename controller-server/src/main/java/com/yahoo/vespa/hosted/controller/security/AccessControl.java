package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * Stores permissions for tenant and application resources.
 *
 * @author jonmv
 */
public interface AccessControl {

    /**
     * Sets up permissions for a tenant, based on the given claim, or throws.
     *
     * @param tenantClaim claim for the tenant to create
     * @param credentials the credentials required to complete this action
     * @param existing list of existing tenants, to check for conflicts
     * @return the created tenant, for keeping
     */
    Tenant createTenant(TenantClaim tenantClaim, Credentials credentials, List<Tenant> existing);

    /**
     * Modifies up permissions for a tenant, based on the given claim, or throws.
     *
     * @param tenantClaim claim for the tenant to update
     * @param credentials the credentials required to complete this action
     * @param existing list of existing tenants, to check for conflicts
     * @param applications list of applications this tenant already owns
     * @return the updated tenant, for keeping
     */
    Tenant updateTenant(TenantClaim tenantClaim, Credentials credentials, List<Tenant> existing, List<Application> applications);

    /**
     * Removes all permissions for tenant in the given claim, and for any applications it owns, or throws.
     *
     * @param tenant the tenant to delete
     * @param credentials the credentials required to complete this action
     */
    void deleteTenant(TenantName tenant, Credentials credentials);

    /**
     * Sets up permissions for an application, based on the given claim, or throws.
     *
     * @param application the ID of the application to create
     * @param credentials the credentials required to complete this action
     */
    void createApplication(ApplicationId application, Credentials credentials);

    /**
     * Removes access control for the given application.
     *
     * @param id the ID of the application to delete
     * @param credentials the credentials required to complete this action
     */
    void deleteApplication(ApplicationId id, Credentials credentials);

    /**
     * Returns the list of tenants to which a principal has access.
     * @param tenants the list of all known tenants
     * @param credentials the credentials of the principal
     * @return the list of tenants the given user has access to
     */
    List<Tenant> accessibleTenants(List<Tenant> tenants, Credentials credentials);

}
