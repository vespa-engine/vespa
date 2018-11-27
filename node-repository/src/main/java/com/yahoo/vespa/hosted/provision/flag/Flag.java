// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.flag;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a feature flag and its status. Use {@link Flags#get(FlagId)} to lookup status for a specific flag.
 *
 * @author mpolden
 */
public class Flag {

    private final FlagId id;
    private final boolean enabled;
    private final Set<String> hostnames;
    private final Set<ApplicationId> applications;

    public Flag(FlagId id, boolean enabled, Set<String> hostnames, Set<ApplicationId> applications) {
        this.id = Objects.requireNonNull(id, "id must be non-null");
        this.enabled = enabled;
        this.hostnames = ImmutableSet.copyOf(Objects.requireNonNull(hostnames, "hostnames must be non-null"));
        this.applications = ImmutableSet.copyOf(Objects.requireNonNull(applications, "applications must be non-null"));
    }

    public FlagId id() {
        return id;
    }

    /** The hostnames this flag should apply to */
    public Set<String> hostnames() {
        return hostnames;
    }

    /** The applications this flag should apply to */
    public Set<ApplicationId> applications() {
        return applications;
    }

    /**
     * Returns whether this flag is enabled for all dimensions. Note: More specific dimensions always return true when
     * this is true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /** Returns whether this flag is enabled for given hostname */
    public boolean isEnabled(HostName hostname) {
        return enabled || hostnames.contains(hostname.value());
    }

    /** Returns whether this flag is enabled for given application */
    public boolean isEnabled(ApplicationId application) {
        return enabled || applications.contains(application);
    }

    /** Returns a copy of this with this flag enabled for all dimensions */
    public Flag enable() {
        return new Flag(id, true, hostnames, applications);
    }

    /** Returns a copy of this with flag enabled for given hostname */
    public Flag enable(HostName hostname) {
        Set<String> hostnames = new LinkedHashSet<>(this.hostnames);
        hostnames.add(hostname.value());
        return new Flag(id, enabled, hostnames, applications);
    }

    /** Returns a copy of this with flag enabled for given application */
    public Flag enable(ApplicationId application) {
        Set<ApplicationId> applications = new LinkedHashSet<>(this.applications);
        applications.add(application);
        return new Flag(id, enabled, hostnames, applications);
    }

    /** Returns a copy of this with flag disabled for given hostname */
    public Flag disable(HostName hostname) {
        Set<String> hostnames = new LinkedHashSet<>(this.hostnames);
        hostnames.remove(hostname.value());
        return new Flag(id, enabled, hostnames, applications);
    }

    /** Returns a copy of this with flag disabled for given application */
    public Flag disable(ApplicationId application) {
        Set<ApplicationId> applications = new LinkedHashSet<>(this.applications);
        applications.remove(application);
        return new Flag(id, enabled, hostnames, applications);
    }

    /** Returns a copy of this with this flag disabled in all dimensions */
    public Flag disable() {
        return new Flag(id, false, hostnames, applications);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Flag flag = (Flag) o;
        return id == flag.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /** Create a flag for given feature that is disabled for all dimensions */
    public static Flag disabled(FlagId id) {
        return new Flag(id, false, Collections.emptySet(), Collections.emptySet());
    }

}
