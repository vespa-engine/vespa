package com.yahoo.vespa.hosted.controller.api.integration.user;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.role.ApplicationRole;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.RoleDefinition;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;

import java.util.List;

/**
 * Validation, utility and serialization methods for roles used in user management.
 *
 * @author jonmv
 */
public class UserRoles {

    /** Creates a new UserRoles which can be used for serialisation and listing of bound user roles. */
    public UserRoles() { }

    /** Returns the list of {@link TenantRole}s a {@link UserId} may be a member of. */
    public List<TenantRole> tenantRoles(TenantName tenant) {
        return List.of(Role.tenantOwner(tenant),
                       Role.tenantAdmin(tenant),
                       Role.tenantOperator(tenant));
    }

    /** Returns the list of {@link ApplicationRole}s a {@link UserId} may be a member of. */
    public List<ApplicationRole> applicationRoles(TenantName tenant, ApplicationName application) {
        return List.of(Role.applicationAdmin(tenant, application),
                       Role.applicationOperator(tenant, application),
                       Role.applicationDeveloper(tenant, application),
                       Role.applicationReader(tenant, application));
    }

    /** Returns the {@link Role} the given value represents. */
    public Role toRole(String value) {
        String[] parts = value.split("\\.");
        if (parts.length == 1) return toOperatorRole(parts[0]);
        if (parts.length == 2) return toRole(TenantName.from(parts[0]), parts[1]);
        if (parts.length == 3) return toRole(TenantName.from(parts[0]), ApplicationName.from(parts[1]), parts[2]);
        throw new IllegalArgumentException("Malformed or illegal role value '" + value + "'.");
    }

    public Role toOperatorRole(String roleName) {
        switch (roleName) {
            case "hostedOperator": return Role.hostedOperator();
            default: throw new IllegalArgumentException("Malformed or illegal role name '" + roleName + "'.");
        }
    }

    /** Returns the {@link Role} the given tenant, application and role names correspond to. */
    public Role toRole(TenantName tenant, String roleName) {
        switch (roleName) {
            case "tenantOwner": return Role.tenantOwner(tenant);
            case "tenantAdmin": return Role.tenantAdmin(tenant);
            case "tenantOperator": return Role.tenantOperator(tenant);
            default: throw new IllegalArgumentException("Malformed or illegal role name '" + roleName + "'.");
        }
    }

    /** Returns the {@link Role} the given tenant and role names correspond to. */
    public Role toRole(TenantName tenant, ApplicationName application, String roleName) {
        switch (roleName) {
            case "applicationAdmin": return Role.applicationAdmin(tenant, application);
            case "applicationOperator": return Role.applicationOperator(tenant, application);
            case "applicationDeveloper": return Role.applicationDeveloper(tenant, application);
            case "applicationReader": return Role.applicationReader(tenant, application);
            default: throw new IllegalArgumentException("Malformed or illegal role name '" + roleName + "'.");
        }
    }

    /** Returns a serialised representation the given role. */
    public static String valueOf(Role role) {
        if (role instanceof TenantRole) return valueOf((TenantRole) role);
        if (role instanceof ApplicationRole) return valueOf((ApplicationRole) role);
        throw new IllegalArgumentException("Unexpected role type '" + role.getClass().getName() + "'.");
    }

    private static String valueOf(TenantRole role) {
        return valueOf(role.tenant()) + "." + valueOf(role.definition());
    }

    private static String valueOf(ApplicationRole role) {
        return valueOf(role.tenant()) + "." + valueOf(role.application()) + "." + valueOf(role.definition());
    }

    private static String valueOf(TenantName tenant) {
        if (tenant.value().contains("."))
            throw new IllegalArgumentException("Tenant names may not contain '.'.");

        return tenant.value();
    }

    private static String valueOf(ApplicationName application) {
        if (application.value().contains("."))
            throw new IllegalArgumentException("Application names may not contain '.'.");

        return application.value();
    }

    private static String valueOf(RoleDefinition role) {
        switch (role) {
            case tenantOwner:          return "tenantOwner";
            case tenantAdmin:          return "tenantAdmin";
            case tenantOperator:       return "tenantOperator";
            case applicationAdmin:     return "applicationAdmin";
            case applicationOperator:  return "applicationOperator";
            case applicationDeveloper: return "applicationDeveloper";
            case applicationReader:    return "applicationReader";
            default: throw new IllegalArgumentException("No value defined for role '" + role + "'.");
        }
    }

}
