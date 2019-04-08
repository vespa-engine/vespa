package com.yahoo.vespa.hosted.controller.api.integration.user;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.role.ApplicationRole;
import com.yahoo.vespa.hosted.controller.api.role.RoleDefinition;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.Roles;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;

import java.util.Objects;

/**
 * An identifier for a role which users identified by {@link UserId}s can be members of, corresponding to a bound {@link Role}.
 *
 * @author jonmv
 */
public class RoleId { // TODO jvenstad: Move serlialisation part to CloudRoles utility class.

    private final String value;

    private RoleId(String value) {
        if (value.isBlank())
            throw new IllegalArgumentException("Id value must be non-blank.");
        this.value = value;
    }

    public static RoleId fromRole(TenantRole role) {
        return new RoleId(valueOf(role));
    }

    public static RoleId fromRole(ApplicationRole role) {
        return new RoleId(valueOf(role));
    }

    public static RoleId fromValue(String value) {
        return new RoleId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoleId id = (RoleId) o;
        return Objects.equals(value, id.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "role '" + value + "'";
    }

    /** Returns the {@link Role} this represent. */
    public Role toRole(Roles roles) {
        String[] parts = value.split("\\.");
        if (parts.length == 2) switch (parts[1]) {
            case "tenantOwner":    return roles.tenantOwner(TenantName.from(parts[0]));
            case "tenantAdmin":    return roles.tenantAdmin(TenantName.from(parts[0]));
            case "tenantOperator": return roles.tenantOperator(TenantName.from(parts[0]));
        }
        if (parts.length == 3) switch (parts[2]) {
            case "applicationAdmin":     return roles.applicationAdmin(TenantName.from(parts[0]), ApplicationName.from(parts[1]));
            case "applicationOperator":  return roles.applicationOperator(TenantName.from(parts[0]), ApplicationName.from(parts[1]));
            case "applicationDeveloper": return roles.applicationDeveloper(TenantName.from(parts[0]), ApplicationName.from(parts[1]));
            case "applicationReader":    return roles.applicationReader(TenantName.from(parts[0]), ApplicationName.from(parts[1]));
        }
        throw new IllegalArgumentException("Malformed or illegal role value '" + value + "'.");
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
