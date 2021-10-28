// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;

import java.util.Objects;
import java.util.Optional;

/**
 * The context in which a role is valid. This is immutable.
 *
 * @author mpolden
 */
class Context {

    private final Optional<TenantName> tenant;
    private final Optional<ApplicationName> application;
    private final Optional<InstanceName> instance;

    private Context(Optional<TenantName> tenant, Optional<ApplicationName> application, Optional<InstanceName> instance) {
        this.tenant = Objects.requireNonNull(tenant, "tenant must be non-null");
        this.application = Objects.requireNonNull(application, "application must be non-null");
        this.instance = Objects.requireNonNull(instance, "instance must be non-null");
    }

    /** A specific tenant this is valid for, if any */
    Optional<TenantName> tenant() {
        return tenant;
    }

    /** A specific application this is valid for, if any */
    Optional<ApplicationName> application() {
        return application;
    }

    /** A specific instance this is valid for, if any */
    Optional<InstanceName> instance() {
        return instance;
    }

    /** Returns a context that has no restrictions on tenant or application */
    static Context unlimited() {
        return new Context(Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Returns a context that is limited to given tenant */
    static Context limitedTo(TenantName tenant) {
        return new Context(Optional.of(tenant), Optional.empty(), Optional.empty());
    }

    /** Returns a context that is limited to given tenant and application */
    static Context limitedTo(TenantName tenant, ApplicationName application) {
        return new Context(Optional.of(tenant), Optional.of(application), Optional.empty());
    }

    /** Returns a context that is limited to given tenant, application, and instance */
    static Context limitedTo(TenantName tenant, ApplicationName application, InstanceName instance) {
        return new Context(Optional.of(tenant), Optional.of(application), Optional.of(instance));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Context context = (Context) o;
        return tenant.equals(context.tenant) &&
               application.equals(context.application) &&
               instance.equals(context.instance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, application, instance);
    }

    @Override
    public String toString() {
        return   "tenant " + tenant.map(TenantName::value).orElse("[none]") + ", "
               + "application " + application.map(ApplicationName::value).orElse("[none]") + ", "
               + "instance " + instance.map(InstanceName::value).orElse("[none]");
    }

}
