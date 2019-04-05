package com.yahoo.vespa.hosted.controller.api.role;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;

import java.util.Objects;

/**
 * Use if you need to create {@link Role}s for its system.
 *
 * This also defines the relationship between {@link RoleDefinition}s and their required {@link Context}s.
 *
 * @author jonmv
 */
public class Roles {

    private final SystemName system;


    @Inject
    public Roles(ZoneRegistry zones) {
        this(zones.system());
    }

    /** Creates a Roles which can be used to create bound roles for the given system. */
    public Roles(SystemName system) {
        this.system = Objects.requireNonNull(system);
    }


    // General roles.
    /** Returns a {@link RoleDefinition#hostedOperator} for the current system. */
    public UnboundRole hostedOperator() {
        return new UnboundRole(RoleDefinition.hostedOperator, system);
    }

    /** Returns a {@link RoleDefinition#everyone} for the current system. */
    public UnboundRole everyone() {
        return new UnboundRole(RoleDefinition.everyone, system);
    }


    // Athenz based roles.
    /** Returns a {@link RoleDefinition#athenzTenantAdmin} for the current system and given tenant. */
    public TenantRole athenzTenantAdmin(TenantName tenant) {
        return new TenantRole(RoleDefinition.athenzTenantAdmin, system, tenant);
    }

    /** Returns a {@link RoleDefinition#tenantPipeline} for the current system and given tenant and application. */
    public ApplicationRole tenantPipeline(TenantName tenant, ApplicationName application) {
        return new ApplicationRole(RoleDefinition.tenantPipeline, system, tenant, application);
    }


    // Other identity provider based roles.
    /** Returns a {@link RoleDefinition#tenantOwner} for the current system and given tenant. */
    public TenantRole tenantOwner(TenantName tenant) {
        return new TenantRole(RoleDefinition.tenantOwner, system, tenant);
    }

    /** Returns a {@link RoleDefinition#tenantAdmin} for the current system and given tenant. */
    public TenantRole tenantAdmin(TenantName tenant) {
        return new TenantRole(RoleDefinition.tenantAdmin, system, tenant);
    }

    /** Returns a {@link RoleDefinition#tenantOperator} for the current system and given tenant. */
    public TenantRole tenantOperator(TenantName tenant) {
        return new TenantRole(RoleDefinition.tenantOperator, system, tenant);
    }

    /** Returns a {@link RoleDefinition#applicationOwner} for the current system and given tenant and application. */
    public ApplicationRole applicationOwner(TenantName tenant, ApplicationName application) {
        return new ApplicationRole(RoleDefinition.applicationOwner, system, tenant, application);
    }

    /** Returns a {@link RoleDefinition#applicationAdmin} for the current system and given tenant and application. */
    public ApplicationRole applicationAdmin(TenantName tenant, ApplicationName application) {
        return new ApplicationRole(RoleDefinition.applicationAdmin, system, tenant, application);
    }

    /** Returns a {@link RoleDefinition#applicationOperator} for the current system and given tenant and application. */
    public ApplicationRole applicationOperator(TenantName tenant, ApplicationName application) {
        return new ApplicationRole(RoleDefinition.applicationOperator, system, tenant, application);
    }

    /** Returns a {@link RoleDefinition#applicationDeveloper} for the current system and given tenant and application. */
    public ApplicationRole applicationDeveloper(TenantName tenant, ApplicationName application) {
        return new ApplicationRole(RoleDefinition.applicationDeveloper, system, tenant, application);
    }

    /** Returns a {@link RoleDefinition#applicationReader} for the current system and given tenant and application. */
    public ApplicationRole applicationReader(TenantName tenant, ApplicationName application) {
        return new ApplicationRole(RoleDefinition.applicationReader, system, tenant, application);
    }

    /** Returns a {@link RoleDefinition#buildService} for the current system and given tenant and application. */
    public ApplicationRole buildService(TenantName tenant, ApplicationName application) {
        return new ApplicationRole(RoleDefinition.buildService, system, tenant, application);
    }

}
