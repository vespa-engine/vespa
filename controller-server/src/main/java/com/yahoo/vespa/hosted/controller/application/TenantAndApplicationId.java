// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;

import java.util.Objects;

/**
 * Tenant and application name pair.
 *
 * @author jonmv
 */
public class TenantAndApplicationId implements Comparable<TenantAndApplicationId> {

    private final TenantName tenant;
    private final ApplicationName application;

    private TenantAndApplicationId(TenantName tenant, ApplicationName application) {
        requireNonBlank(tenant.value(), "Tenant name");
        requireNonBlank(application.value(), "Application name");
        this.tenant = tenant;
        this.application = application;
    }

    public static TenantAndApplicationId from(TenantName tenant, ApplicationName application) {
        return new TenantAndApplicationId(tenant, application);
    }

    public static TenantAndApplicationId from(String tenant, String application) {
        return from(TenantName.from(tenant), ApplicationName.from(application));
    }

    public static TenantAndApplicationId fromSerialized(String value) {
        String[] parts = value.split(":");
        if (parts.length != 2)
            throw new IllegalArgumentException("Serialized value should be '<tenant>:<application>', but was '" + value + "'");

        return from(parts[0], parts[1]);
    }

    public static TenantAndApplicationId from(ApplicationId id) {
        return from(id.tenant(), id.application());
    }

    public ApplicationId defaultInstance() {
        return instance(InstanceName.defaultName());
    }

    public ApplicationId instance(String instance) {
        return instance(InstanceName.from(instance));
    }

    public ApplicationId instance(InstanceName instance) {
        return ApplicationId.from(tenant, application, instance);
    }

    public String serialized() {
        return tenant.value() + ":" + application.value();
    }

    public TenantName tenant() {
        return tenant;
    }

    public ApplicationName application() {
        return application;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        TenantAndApplicationId that = (TenantAndApplicationId) other;
        return tenant.equals(that.tenant) &&
               application.equals(that.application);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, application);
    }

    @Override
    public int compareTo(TenantAndApplicationId other) {
        int tenantComparison = tenant.compareTo(other.tenant);
        return tenantComparison != 0 ? tenantComparison : application.compareTo(other.application);
    }

    @Override
    public String toString() {
        return tenant.value() + "." + application.value();
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " cannot be null");
        if (name.isBlank())
            throw new IllegalArgumentException(name + " cannot be blank");
    }

}
