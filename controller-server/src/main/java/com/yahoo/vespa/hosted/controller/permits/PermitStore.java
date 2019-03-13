package com.yahoo.vespa.hosted.controller.permits;

/**
 * Stores permits for accessing tenant and application resources.
 *
 * @author jonmv
 */
public interface PermitStore {

    /** Creates a tenant with permissions given by the permit. */
    void createTenant(TenantPermit tenantPermit);

    /** Deletes the tenant and all permissions related to it. */
    void deleteTenant(TenantPermit tenantPermit);

    /** Creates an application resource with permissions given by the permit. */
    void createApplication(ApplicationPermit applicationPermit);

    /** Deletes the application and all permissions related to it. */
    void deleteApplication(ApplicationPermit applicationPermit);

}
