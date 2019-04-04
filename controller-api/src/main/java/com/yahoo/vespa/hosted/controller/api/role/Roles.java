package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;

import java.util.Objects;

/**
 * Use if you need to create {@link Role}s for its system.
 *
 * This also defines the relationship between {@link ProtoRole}s and their required {@link Context}s.
 *
 * @author jonmv
 */
public class Roles {

    private final SystemName system;

    /** Create a Roles which can be used to create bound roles for the given system. */
    public Roles(SystemName system) {
        this.system = Objects.requireNonNull(system);
    }

    // General roles.
    /** Returns a {@link ProtoRole#hostedOperator} for the current system. */
    public UnboundRole hostedOperator() {
        return new UnboundRole(ProtoRole.hostedOperator, system);
    }

    /** Returns a {@link ProtoRole#everyone} for the current system. */
    public UnboundRole everyone() {
        return new UnboundRole(ProtoRole.everyone, system);
    }

    // Athenz based roles.
    /** Returns a {@link ProtoRole#athenzTenantAdmin} for the current system and given tenant. */
    public TenantRole athenzTenantAdmin(TenantName tenant) {
        return new TenantRole(ProtoRole.athenzTenantAdmin, system, tenant);
    }

    /** Returns a {@link ProtoRole#tenantPipeline} for the current system and given tenant and application. */
    public ApplicationRole tenantPipeline(TenantName tenant, ApplicationName application) {
        return new ApplicationRole(ProtoRole.tenantPipeline, system, tenant, application);
    }

    // Other identity provider based roles.
    /** Returns a {@link ProtoRole#tenantOwner} for the current system and given tenant. */
    public TenantRole tenantOwner(TenantName tenant) {
        return new TenantRole(ProtoRole.tenantOwner, system, tenant);
    }

    /** Returns a {@link ProtoRole#tenantAdmin} for the current system and given tenant. */
    public TenantRole tenantAdmin(TenantName tenant) {
        return new TenantRole(ProtoRole.tenantAdmin, system, tenant);
    }

    /** Returns a {@link ProtoRole#tenantOperator} for the current system and given tenant. */
    public TenantRole tenantOperator(TenantName tenant) {
        return new TenantRole(ProtoRole.tenantOperator, system, tenant);
    }

    /** Returns a {@link ProtoRole#applicationOwner} for the current system and given tenant and application. */
    public ApplicationRole applicationOwner(TenantName tenant, ApplicationName application) {
        return new ApplicationRole(ProtoRole.applicationOwner, system, tenant, application);
    }

    /** Returns a {@link ProtoRole#applicationAdmin} for the current system and given tenant and application. */
    public ApplicationRole applicationAdmin(TenantName tenant, ApplicationName application) {
        return new ApplicationRole(ProtoRole.applicationAdmin, system, tenant, application);
    }

    /** Returns a {@link ProtoRole#applicationOperator} for the current system and given tenant and application. */
    public ApplicationRole applicationOperator(TenantName tenant, ApplicationName application) {
        return new ApplicationRole(ProtoRole.applicationOperator, system, tenant, application);
    }

    /** Returns a {@link ProtoRole#applicationDeveloper} for the current system and given tenant and application. */
    public ApplicationRole applicationDeveloper(TenantName tenant, ApplicationName application) {
        return new ApplicationRole(ProtoRole.applicationDeveloper, system, tenant, application);
    }

    /** Returns a {@link ProtoRole#applicationReader} for the current system and given tenant and application. */
    public ApplicationRole applicationReader(TenantName tenant, ApplicationName application) {
        return new ApplicationRole(ProtoRole.applicationReader, system, tenant, application);
    }

}
