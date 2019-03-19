package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * Keeps permits for tenant and application resources.
 *
 * @author jonmv
 */
public interface AccessControlManager {

    /**
     * Sets up permissions for a tenant, based on the given permit, or throws.
     *
     * @param tenantPermit permit for the tenant to create
     * @param existing list of existing tenants, to check for conflicts
     * @param applications list of applications this tenant already owns
     * @return the created tenant, for keeping
     */
    Tenant createTenant(TenantPermit tenantPermit, List<Tenant> existing, List<Application> applications);

    /**
     * Removes all permissions for tenant in the given permit, and for any applications it owns, or throws.
     *
     * @param tenantPermit permit for the tenant to delete
     * @param tenant the tenant to delete
     * @param applications list of applications this tenant owns
     */
    void deleteTenant(TenantPermit tenantPermit, Tenant tenant, List<Application> applications);

    /**
     * Sets up permissions for an application, based on the given permit, or throws.
     *
     * @param applicationPermit permit for the application to create
     */
    void createApplication(ApplicationPermit applicationPermit);

    /**
     * Removes permissions for the application in the given permit, or throws.
     *
     * @param applicationPermit permit for the application to delete
     */
    void deleteApplication(ApplicationPermit applicationPermit);

    /**
     * Returns the list of tenants to which this principal has access.
     * @param tenants the list of all known tenants
     * @param principal the user whose tenants to return
     * @return the list of tenants the given user has access to
     */
    List<Tenant> accessibleTenants(List<Tenant> tenants, Principal principal);

}
