// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * JSON value of the {@code pyroscope-profiling} feature flag
 * (see {@link com.yahoo.vespa.flags.PermanentFlags#PYROSCOPE_PROFILING}), which selects the services to
 * continuously profile for one application. Missing fields fall back to the disabled defaults.
 *
 * <p>The flag is resolved per application, so on a shared host each tenant's nodes get their own
 * value and only the listed services of the enabled applications are profiled.</p>
 *
 * @author onur
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfilingSettings {

    private final boolean enabled;
    private final List<ProfiledService> services;

    public static ProfilingSettings createDisabled() {
        return new ProfilingSettings(false, null);
    }

    @JsonCreator
    public ProfilingSettings(@JsonProperty("enabled") Boolean enabled,
                             @JsonProperty("services") List<ProfiledService> services) {
        this.enabled = enabled != null && enabled;
        this.services = services == null ? List.of() : List.copyOf(services);
    }

    @JsonGetter("enabled")  public boolean enabled()                { return enabled; }
    @JsonGetter("services") public List<ProfiledService> services() { return services; }

    /**
     * The services to profile: none unless the flag is both enabled and lists at least one service,
     * so that neither switch alone starts profiling anything.
     */
    public Set<ProfiledService> profiledServices() {
        return enabled ? Set.copyOf(services) : Set.of();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfilingSettings that = (ProfilingSettings) o;
        return enabled == that.enabled && services.equals(that.services);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, services);
    }

}
