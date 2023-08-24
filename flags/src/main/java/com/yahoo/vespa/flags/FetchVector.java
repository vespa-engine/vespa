// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.vespa.flags.json.DimensionHelper;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Denotes which RawFlag should be retrieved from {@link FlagSource} for a given {@link FlagId},
 * as the raw flag may depend on the hostname, application, etc.
 *
 * @author hakonhall
 */
public class FetchVector {
    /**
     * Note: If this enum is changed, you must also change {@link DimensionHelper}.
     */
    public enum Dimension {
        /** Value from ApplicationId::serializedForm of the form tenant:applicationName:instance. */
        APPLICATION_ID,

        /**
         * Cloud from com.yahoo.config.provision.CloudName::value, e.g. yahoo, aws, gcp.
         *
         * <p><em>Eager resolution</em>:  This dimension is resolved before putting the flag data to the config server
         * or controller, unless controller and the flag has declared this dimension.
         */
        CLOUD,

        /**
         * Cloud account ID from com.yahoo.config.provision.CloudAccount::value, e.g. aws:123456789012
         */
        CLOUD_ACCOUNT,

        /** Cluster ID from com.yahoo.config.provision.ClusterSpec.Id::value, e.g. cluster-controllers, logserver. */
        CLUSTER_ID,

        /** Cluster type from com.yahoo.config.provision.ClusterSpec.Type::name, e.g. content, container, admin */
        CLUSTER_TYPE,

        /** Email address of user - provided by auth0 in console. */
        CONSOLE_USER_EMAIL,

        /** Hosted Vespa environment from com.yahoo.config.provision.Environment::value, e.g. prod, staging, test. */
        ENVIRONMENT,

        /**
         * Fully qualified hostname.
         *
         * <p>NOTE: There is seldom any need to set HOSTNAME, as it is always set implicitly (in {@link Flags})
         * from {@code Defaults.getDefaults().vespaHostname()}. The hostname may e.g. be overridden when
         * fetching flag value for a Docker container node.
         */
        HOSTNAME,

        /** Node type from com.yahoo.config.provision.NodeType::name, e.g. tenant, host, confighost, controller, etc. */
        NODE_TYPE,

        /**
         * Hosted Vespa system from com.yahoo.config.provision.SystemName::value, e.g. main, cd, public, publiccd.
         * <em>Eager resolution</em>, see {@link #CLOUD}.
         */
        SYSTEM,

        /** Value from TenantName::value, e.g. vespa-team */
        TENANT_ID,

        /**
         * Vespa version from Version::toFullString of the form Major.Minor.Micro.
         *
         * <p>NOTE: There is seldom any need to set VESPA_VERSION, as it is always set implicitly
         * (in {@link Flags}) from {@link com.yahoo.component.Vtag#currentVersion}. The version COULD e.g.
         * be overridden when fetching flag value for a Docker container node.
         */
        VESPA_VERSION,

        /**
         * Virtual zone ID from com.yahoo.config.provision.zone.ZoneId::value of the form environment.region,
         * see com.yahoo.config.provision.zone.ZoneApi::getVirtualId.  <em>Eager resolution</em>, see {@link #CLOUD}.
         */
        ZONE_ID
    }

    private final Map<Dimension, String> map;

    public FetchVector() {
        this.map = Map.of();
    }

    public static FetchVector fromMap(Map<Dimension, String> map) {
        return new FetchVector(map);
    }

    private FetchVector(Map<Dimension, String> map) {
        this.map = Map.copyOf(map);
    }

    public Optional<String> getValue(Dimension dimension) {
        return Optional.ofNullable(map.get(dimension));
    }

    public Map<Dimension, String> toMap() { return map; }

    public boolean isEmpty() { return map.isEmpty(); }

    public boolean hasDimension(FetchVector.Dimension dimension) { return map.containsKey(dimension);}

    public Set<Dimension> dimensions() { return map.keySet(); }

    /**
     * Returns a new FetchVector, identical to {@code this} except for its value in {@code dimension}.
     * Dimension is removed if the value is null.
     */
    public FetchVector with(Dimension dimension, String value) {
        if (value == null) return makeFetchVector(merged -> merged.remove(dimension));
        return makeFetchVector(merged -> merged.put(dimension, value));
    }

    /** Returns a new FetchVector, identical to {@code this} except for its values in the override's dimensions. */
    public FetchVector with(FetchVector override) {
        return makeFetchVector(vector -> vector.putAll(override.map));
    }

    private FetchVector makeFetchVector(Consumer<Map<Dimension, String>> mapModifier) {
        Map<Dimension, String> mergedMap = new EnumMap<>(Dimension.class);
        mergedMap.putAll(map);
        mapModifier.accept(mergedMap);
        return new FetchVector(mergedMap);
    }

    public FetchVector without(Dimension dimension) {
        return makeFetchVector(merged -> merged.remove(dimension));
    }

    public FetchVector without(Collection<Dimension> dimensions) {
        return makeFetchVector(merged -> merged.keySet().removeAll(dimensions));
    }

    @Override
    public String toString() {
        return "FetchVector{" +
               "map=" + map +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FetchVector that = (FetchVector) o;
        return Objects.equals(map, that.map);
    }

    @Override
    public int hashCode() {
        return Objects.hash(map);
    }
}
