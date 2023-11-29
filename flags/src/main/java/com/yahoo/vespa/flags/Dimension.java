// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Flag dimensions.
 *
 * <ol>
 *     <li>A flag definition declares the dimensions it supports.*</li>
 *     <li>To <em>get</em> the value of a flag, a {@link FetchVector} should be built with the same set of dimensions.*</li>
 *     <li>To <em>set</em> the value of a flag, add a flag data <em>rule</em> with that value.  The rule may be
 *     contain conditions that refer to these dimension.</li>
 * </ol>
 *
 * <p>*)  The system, cloud, environment, and zone dimensions are special:  A flag should NOT list them in the
 * flag definition (1),** and the dimensions are automatically set (2),**.  These dimensions may always be referred to
 * when overriding values, either as dimensions (3) or in dedicated files.</p>
 * <p>**)  The controller may want different flag values depending on cloud, environment, and/or zone.
 * Disregard (*) for those dimensions: The relevant dimensions should be declared in the flag definition (1),
 * and specified when getting the value (2).</p>
 *
 * @author hakonhall
 */
public enum Dimension {
    /**
     * Application from ApplicationId::toSerializedFormWithoutInstance() of the form tenant:applicationName.
     * <p><em>WARNING: NOT ApplicationId</em> - see {@link #INSTANCE_ID}.</p>
     */
    APPLICATION("application"),

    /** Machine architecture: either arm64 or x86_64. */
    ARCHITECTURE("architecture"),

    /** Whether "enclave" (or "inclave" or "exclave"), or not ("noclave"). */
    CLAVE("clave"),

    /**
     * Cloud from com.yahoo.config.provision.CloudName::value, e.g. yahoo, aws, gcp.
     *
     * <p><em>Eager resolution</em>:  This dimension is resolved before putting the flag data to the config server
     * or controller, unless controller and the flag has declared this dimension.
     */
    CLOUD("cloud"),

    /** Cloud account ID from com.yahoo.config.provision.CloudAccount::value, e.g. aws:123456789012 */
    CLOUD_ACCOUNT("cloud-account"),

    /** Cluster ID from com.yahoo.config.provision.ClusterSpec.Id::value, e.g. cluster-controllers, logserver. */
    CLUSTER_ID("cluster-id"),

    /** Cluster type from com.yahoo.config.provision.ClusterSpec.Type::name, e.g. content, container, admin */
    CLUSTER_TYPE("cluster-type"),

    /** Email address of user - provided by auth0 in console. */
    CONSOLE_USER_EMAIL("console-user-email"),

    /** Hosted Vespa environment from com.yahoo.config.provision.Environment::value, e.g. prod, staging, test. */
    ENVIRONMENT("environment"),

    /**
     * Fully qualified hostname.
     *
     * <p>NOTE: There is seldom any need to set HOSTNAME, as it is always set implicitly (in {@link Flags})
     * from {@code Defaults.getDefaults().vespaHostname()}. The hostname may e.g. be overridden when
     * fetching flag value for a Docker container node.
     */
    HOSTNAME("hostname"),

    /** Value from ApplicationId::serializedForm of the form tenant:applicationName:instance. */
    INSTANCE_ID("instance"),

    /** Node type from com.yahoo.config.provision.NodeType::name, e.g. tenant, host, confighost, controller, etc. */
    NODE_TYPE("node-type"),

    /**
     * Hosted Vespa system from com.yahoo.config.provision.SystemName::value, e.g. main, cd, public, publiccd.
     * <em>Eager resolution</em>, see {@link #CLOUD}.
     */
    SYSTEM("system"),

    /** Value from TenantName::value, e.g. vespa-team */
    TENANT_ID("tenant"),

    /**
     * Vespa version from Version::toFullString of the form Major.Minor.Micro.
     *
     * <p>NOTE: There is seldom any need to set VESPA_VERSION, as it is always set implicitly
     * (in {@link Flags}) from {@link com.yahoo.component.Vtag#currentVersion}. The version COULD e.g.
     * be overridden when fetching flag value for a Docker container node.
     */
    VESPA_VERSION("vespa-version"),

    /**
     * Virtual zone ID from com.yahoo.config.provision.zone.ZoneId::value of the form environment.region,
     * see com.yahoo.config.provision.zone.ZoneApi::getVirtualId.  <em>Eager resolution</em>, see {@link #CLOUD}.
     */
    ZONE_ID("zone");

    private final String wireName;

    private static final Map<String, Dimension> dimensionsByWireName =
            Stream.of(values()).collect(Collectors.toMap(x -> x.wireName, Function.identity()));

    public static Dimension fromWire(String wireName) {
        Dimension dimension = dimensionsByWireName.get(wireName);
        if (dimension == null) {
            throw new IllegalArgumentException("Unknown serialized dimension: '" + wireName + "'");
        }

        return dimension;
    }

    Dimension(String wireName) { this.wireName = wireName; }

    public String toWire() { return wireName; }
}
