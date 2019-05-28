// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;

import java.util.Objects;
import java.util.Optional;

/**
 * The zone (environment + region) of this runtime, and some other information.
 * An injected instance of this will return the correct current environment and region.
 * Components can use this to obtain information about which zone they are running in.
 *
 * @author bratseth
 */
public class Zone {

    private final CloudName cloudName;
    private final SystemName systemName;
    private final Environment environment;
    private final RegionName region;
    private final Optional<NodeFlavors> nodeFlavors;

    @Inject
    public Zone(ConfigserverConfig configserverConfig, NodeFlavors nodeFlavors) {
        this(CloudName.from(configserverConfig.cloud()),
             SystemName.from(configserverConfig.system()),
             Environment.from(configserverConfig.environment()),
             RegionName.from(configserverConfig.region()),
             nodeFlavors);
    }

    /** Create from environment and region. Use for testing.  */
    public Zone(Environment environment, RegionName region) {
        this(SystemName.defaultSystem(), environment, region);
    }

    /** Create from system, environment and region. Use for testing. */
    public Zone(SystemName systemName, Environment environment, RegionName region) {
        this(CloudName.defaultName(), systemName, environment, region);
    }

    /** Create from cloud, system, environment and region. Use for testing. */
    public Zone(CloudName cloudName, SystemName systemName, Environment environment, RegionName region) {
        this(cloudName, systemName, environment, region, null);
    }

    /** Create from cloud, system, environment, region and node flavors. Use for testing. */
    private Zone(CloudName cloudName,
                 SystemName systemName,
                 Environment environment,
                 RegionName region,
                 NodeFlavors nodeFlavors) {
        this.cloudName = cloudName;
        this.systemName = systemName;
        this.environment = environment;
        this.region = region;
        this.nodeFlavors = Optional.ofNullable(nodeFlavors);
    }

    /** Returns the current cloud */
    public CloudName cloud() { return cloudName; }

    /** Returns the current system */
    public SystemName system() { return systemName; }

    /** Returns the current environment */
    public Environment environment() {
        return environment;
    }

    /** Returns the current region */
    public RegionName region() {
        return region;
    }

    /** Returns all available node flavors for the zone, or empty if not set for this Zone. */
    public Optional<NodeFlavors> nodeFlavors() { return nodeFlavors; }

    /** Do not use */
    public static Zone defaultZone() {
        return new Zone(CloudName.defaultName(), SystemName.defaultSystem(), Environment.defaultEnvironment(), RegionName.defaultName());
    }

    @Override
    public String toString() {
        return "zone " + environment + "." + region;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Zone)) return false;
        Zone zone = (Zone) o;
        return environment == zone.environment &&
               Objects.equals(region, zone.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(environment, region);
    }

}

