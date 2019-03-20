package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.security.Principal;
import java.util.List;

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
     * @param existing list of existing tenants, to check for conflicts
     * @return the created tenant, for keeping
     */
    Tenant createTenant(TenantClaim tenantClaim, List<Tenant> existing);

    /**
     * Modifies up permissions for a tenant, based on the given claim, or throws.
     *
     * @param tenantClaim claim for the tenant to update
     * @param existing list of existing tenants, to check for conflicts
     * @param applications list of applications this tenant already owns
     * @return the updated tenant, for keeping
     */
    Tenant updateTenant(TenantClaim tenantClaim, List<Tenant> existing, List<Application> applications);

    /**
     * Removes all permissions for tenant in the given claim, and for any applications it owns, or throws.
     *
     * @param tenantClaim claim for the tenant to delete
     * @param tenant the tenant to delete
     */
    void deleteTenant(TenantClaim tenantClaim, Tenant tenant);

    /**
     * Sets up permissions for an application, based on the given claim, or throws.
     *
     * @param applicationClaim claim for the application to create
     */
    void createApplication(ApplicationClaim applicationClaim);

    /**
     * Removes permissions for the application in the given claim, or throws.
     *
     * @param applicationClaim claim for the application to delete
     */
    void deleteApplication(ApplicationClaim applicationClaim);

    /**
     * Returns the list of tenants to which this principal has access.
     * @param tenants the list of all known tenants
     * @param principal the user whose tenants to return
     * @return the list of tenants the given user has access to
     */
    List<Tenant> accessibleTenants(List<Tenant> tenants, Principal principal);

}
