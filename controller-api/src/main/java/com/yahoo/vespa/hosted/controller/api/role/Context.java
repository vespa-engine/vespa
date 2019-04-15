// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;

import java.util.Objects;
import java.util.Optional;

/**
 * The context in which a role is valid. This is immutable.
 *
 * @author mpolden
 */
public class Context {

    private final Optional<TenantName> tenant;
    private final Optional<ApplicationName> application;

    private Context(Optional<TenantName> tenant, Optional<ApplicationName> application) {
        this.tenant = Objects.requireNonNull(tenant, "tenant must be non-null");
        this.application = Objects.requireNonNull(application, "application must be non-null");
    }

    /** A specific tenant this is valid for, if any */
    public Optional<TenantName> tenant() {
        return tenant;
    }

    /** A specific application this is valid for, if any */
    public Optional<ApplicationName> application() {
        return application;
    }

    /** Returns a context that has no restrictions on tenant or application */
    public static Context unlimited() {
        return new Context(Optional.empty(), Optional.empty());
    }

    /** Returns a context that is limited to given tenant */
    public static Context limitedTo(TenantName tenant) {
        return new Context(Optional.of(tenant), Optional.empty());
    }

    /** Returns a context that is limited to given tenant, application */
    public static Context limitedTo(TenantName tenant, ApplicationName application) {
        return new Context(Optional.of(tenant), Optional.of(application));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Context context = (Context) o;
        return tenant.equals(context.tenant) &&
               application.equals(context.application);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, application);
    }

    @Override
    public String toString() {
        return "tenant " + tenant.map(TenantName::value).orElse("[none]") + ", application " +
               application.map(ApplicationName::value).orElse("[none]");
    }

}
