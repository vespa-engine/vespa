// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.vespa.flags.json.DimensionHelper;

import javax.annotation.concurrent.Immutable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Denotes which RawFlag should be retrieved from {@link FlagSource} for a given {@link FlagId},
 * as the raw flag may depend on the hostname, application, etc.
 *
 * @author hakonhall
 */
@Immutable
public class FetchVector {
    /**
     * Note: If this enum is changed, you must also change {@link DimensionHelper}.
     */
    public enum Dimension {
        /** A legal value for TenantName, e.g. vespa-team */
        TENANT_ID,

        /** Value from ApplicationId::serializedForm of the form tenant:applicationName:instance. */
        APPLICATION_ID,

        /** Node type from com.yahoo.config.provision.NodeType::name, e.g. tenant, host, confighost, controller, etc. */
        NODE_TYPE,

        /** Cluster type from com.yahoo.config.provision.ClusterSpec.Type::name, e.g. content, container, admin */
        CLUSTER_TYPE,

        /**
         * Fully qualified hostname.
         *
         * <p>NOTE: There is seldom any need to set HOSTNAME, as it is always set implicitly (in {@link Flags})
         * from {@code Defaults.getDefaults().vespaHostname()}. The hostname may e.g. be overridden when
         * fetching flag value for a Docker container node.
         */
        HOSTNAME,

        /**
         * Vespa version from Version::toFullString of the form Major.Minor.Micro.
         *
         * <p>NOTE: There is seldom any need to set VESPA_VERSION, as it is always set implicitly
         * (in {@link Flags}) from {@link com.yahoo.component.Vtag#currentVersion}. The version COULD e.g.
         * be overridden when fetching flag value for a Docker container node.
         */
        VESPA_VERSION,

        /** Email address of user - provided by auth0 in console. */
        CONSOLE_USER_EMAIL,

        /**
         * Zone from ZoneId::value of the form environment.region.
         *
         * <p>NOTE: There is seldom any need to set ZONE_ID, as all flags are set per zone anyways. The controller
         * could PERHAPS use this where it handles multiple zones.
         */
        ZONE_ID
    }

    private final Map<Dimension, String> map;

    public FetchVector() {
        this.map = Collections.emptyMap();
    }

    public static FetchVector fromMap(Map<Dimension, String> map) {
        return new FetchVector(new HashMap<>(map));
    }

    private FetchVector(Map<Dimension, String> map) {
        this.map = Collections.unmodifiableMap(map);
    }

    public Optional<String> getValue(Dimension dimension) {
        return Optional.ofNullable(map.get(dimension));
    }

    public Map<Dimension, String> toMap() {
        return map;
    }

    public boolean isEmpty() { return map.isEmpty(); }

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

    private FetchVector makeFetchVector(Consumer<EnumMap<Dimension, String>> mapModifier) {
        EnumMap<Dimension, String> mergedMap = new EnumMap<>(Dimension.class);
        mergedMap.putAll(map);
        mapModifier.accept(mergedMap);
        return new FetchVector(mergedMap);
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
