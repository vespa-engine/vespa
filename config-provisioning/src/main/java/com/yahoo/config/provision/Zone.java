// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;

import java.util.Optional;

/**
 * The zone (environment + region) of this runtime.
 * An injected instance of this will return the correct current environment and region.
 * Components can use this to obtain information about which zone they are running in.
 *
 * @author bratseth
 */
public class Zone {

    private final Environment environment;
    private final RegionName region;
    private final SystemName systemName;
    private final FlavorDefaults flavorDefaults;
    private final Optional<NodeFlavors> nodeFlavors;

    @Inject
    public Zone(ConfigserverConfig configserverConfig, NodeFlavors nodeFlavors) {
        this(SystemName.from(configserverConfig.system()),
             Environment.from(configserverConfig.environment()),
             RegionName.from(configserverConfig.region()),
             new FlavorDefaults(configserverConfig),
             nodeFlavors);
    }

    /** Create from environment and region */
    public Zone(Environment environment, RegionName region) {
        this(SystemName.defaultSystem(), environment, region, "default");
    }

    /** Create from system, environment and region */
    public Zone(SystemName systemName, Environment environment, RegionName region) {
        this(systemName, environment, region, "default");
    }

    /** Create from environment and region. Useful for testing. */
    public Zone(SystemName system, Environment environment, RegionName region, String defaultFlavor) {
        this(system, environment, region, new FlavorDefaults(defaultFlavor), null);
    }

    private Zone(SystemName systemName,
                 Environment environment,
                 RegionName region,
                 FlavorDefaults flavorDefaults,
                 NodeFlavors nodeFlavors) {
        this.environment = environment;
        this.region = region;
        this.flavorDefaults = flavorDefaults;
        this.systemName = systemName;
        this.nodeFlavors = Optional.ofNullable(nodeFlavors);
    }

    /** Returns the current environment */
    public Environment environment() { return environment; }

    /** Returns the current region */
    public RegionName region() { return region; }

    /** Returns the current system */
    public SystemName system() { return systemName; }

    /** Returns the default hardware flavor to assign in this zone */
    public String defaultFlavor(ClusterSpec.Type clusterType) { return flavorDefaults.flavor(clusterType); }

    /** Returns all available node flavors for the zone, or empty if not set for this Zone. */
    public Optional<NodeFlavors> nodeFlavors() { return nodeFlavors; }

    /** Do not use */
    public static Zone defaultZone() {
        return new Zone(SystemName.defaultSystem(), Environment.defaultEnvironment(), RegionName.defaultName());
    }

    @Override
    public String toString() {
        return "zone " + environment + "." + region;
    }
    
    @Override
    public int hashCode() { return environment().hashCode() + 7 * region.hashCode();}
    
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Zone)) return false;
        
        Zone other = (Zone)o;
        if ( this.environment() != other.environment()) return false;
        if ( ! this.region.equals(other.region)) return false;
        return true;
    }

    private static class FlavorDefaults {

        /** The default default flavor */
        private final String defaultFlavor;

        /** The default flavor for each cluster type, or empty to use defaultFlavor */
        private final Optional<String> adminFlavor;
        private final Optional<String> containerFlavor;
        private final Optional<String> contentFlavor;

        /** Creates this with a default flavor and all cluster type flavors empty */
        public FlavorDefaults(String defaultFlavor) {
            this(defaultFlavor, Optional.empty(), Optional.empty(), Optional.empty());
        }

        /** Creates this with a default flavor and all cluster type flavors empty */
        public FlavorDefaults(String defaultFlavor,
                              Optional<String> adminFlavor, Optional<String> containerFlavor, Optional<String> contentFlavor) {
            this.defaultFlavor = defaultFlavor;
            this.adminFlavor = adminFlavor;
            this.containerFlavor = containerFlavor;
            this.contentFlavor = contentFlavor;
        }

        public FlavorDefaults(ConfigserverConfig config) {
            this(config.defaultFlavor(),
                 emptyIfDefault(config.defaultAdminFlavor()),
                 emptyIfDefault(config.defaultContainerFlavor()),
                 emptyIfDefault(config.defaultContentFlavor()));
        }

        /** Map "default" to empty - this config cannot have missing values due to the need for supporting non-hosted */
        private static Optional<String> emptyIfDefault(String value) {
            if (Strings.isNullOrEmpty(value)) return Optional.empty();
            if (value.equals("default")) return Optional.empty();
            return Optional.of(value);
        }

        /**
         * Returns the flavor default for a given cluster type.
         * This may be "default" - which is an invalid value - but never null.
         */
        public String flavor(ClusterSpec.Type clusterType) {
            switch (clusterType) {
                case admin: return adminFlavor.orElse(defaultFlavor);
                case container: return containerFlavor.orElse(defaultFlavor);
                case content: return contentFlavor.orElse(defaultFlavor);
                default: return defaultFlavor; // future cluster types
            }
        }

    }

}
