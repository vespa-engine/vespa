// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provisioning.CloudConfig;

import java.util.Objects;

/**
 * The zone (environment + region) of this runtime, and some other information.
 * An injected instance of this will return the correct current environment and region.
 * Components can use this to obtain information about which zone they are running in.
 *
 * @author bratseth
 */
public class Zone {

    private final Cloud cloud;
    private final SystemName systemName;
    private final Environment environment;
    private final RegionName region;

    @Inject
    public Zone(ConfigserverConfig configserverConfig, CloudConfig cloudConfig) {
        this(Cloud.builder()
                  .name(CloudName.from(configserverConfig.cloud()))
                  .dynamicProvisioning(cloudConfig.dynamicProvisioning())
                  .reprovisionToUpgradeOs(cloudConfig.reprovisionToUpgradeOs())
                  .requireAccessControl(cloudConfig.requireAccessControl())
                  .build(),
             SystemName.from(configserverConfig.system()),
             Environment.from(configserverConfig.environment()),
             RegionName.from(configserverConfig.region()));
    }

    /** Create from environment and region. Use for testing.  */
    public Zone(Environment environment, RegionName region) {
        this(SystemName.defaultSystem(), environment, region);
    }

    /** Create from system, environment and region. Use for testing. */
    public Zone(SystemName systemName, Environment environment, RegionName region) {
        this(Cloud.defaultCloud(), systemName, environment, region);
    }

    /** Create from cloud, system, environment and region. Also used for testing. */
    public Zone(Cloud cloud, SystemName systemName, Environment environment, RegionName region) {
        this.cloud = cloud;
        this.systemName = systemName;
        this.environment = environment;
        this.region = region;
    }

    /** Returns the current cloud */
    public Cloud getCloud() { return cloud; }

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

    /** Do not use */
    public static Zone defaultZone() {
        return new Zone(Cloud.defaultCloud(), SystemName.defaultSystem(), Environment.defaultEnvironment(), RegionName.defaultName());
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

