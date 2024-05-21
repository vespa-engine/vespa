// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources.Architecture;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.Optional;

/**
 * Interface for flag.
 *
 * @param <T> The type of the flag value (boxed for primitives)
 * @param <F> The concrete subclass type of the flag
 * @author hakonhall
 */
public interface Flag<T, F extends Flag<T, F>> {
    /** The flag ID. */
    FlagId id();

    /** A generic type-safe method for getting {@code this}. */
    F self();

    /** Returns the flag serializer. */
    FlagSerializer<T> serializer();

    /** Returns an immutable clone of the current object, except with the dimension set accordingly. */
    F with(Dimension dimension, String dimensionValue);

    /** Same as {@link #with(Dimension, String)} if value is present, and otherwise returns {@code this}. */
    default F with(Dimension dimension, Optional<String> dimensionValue) {
        return dimensionValue.map(value -> with(dimension, value)).orElse(self());
    }

    /** Sets the tenant, application, and instance dimensions. */
    default F with(ApplicationId applicationId) {
        return with(Dimension.TENANT_ID, applicationId.tenant().value())
              .with(Dimension.APPLICATION, applicationId.toSerializedFormWithoutInstance())
              .with(Dimension.INSTANCE_ID, applicationId.serializedForm());
    }

    /** architecture MUST NOT be 'any'. */
    default F with(Architecture architecture) { return with(Dimension.ARCHITECTURE, architecture.name()); }
    default F with(CloudName cloud) { return with(Dimension.CLOUD, cloud.value()); }
    default F with(ClusterSpec.Id clusterId) { return with(Dimension.CLUSTER_ID, clusterId.value()); }
    default F with(ClusterSpec.Type clusterType) { return with(Dimension.CLUSTER_TYPE, clusterType.name()); }
    default F with(Environment environment) { return with(Dimension.ENVIRONMENT, environment.value()); }
    default F with(NodeType nodeType) { return with(Dimension.NODE_TYPE, nodeType.name()); }
    default F with(SystemName systemName) { return with(Dimension.SYSTEM, systemName.value()); }
    default F with(TenantName tenantName) { return with(Dimension.TENANT_ID, tenantName.value()); }
    default F with(Version vespaVersion) { return with(Dimension.VESPA_VERSION, vespaVersion.toFullString()); }
    default F with(ZoneId zoneId) { return with(Dimension.ZONE_ID, zoneId.value()); }
    default F with(Zone zone) { return with(Dimension.ZONE_ID, zone.systemLocalValue()); }
    default F with(ZoneApi zoneApi) { return with(zoneApi.getVirtualId()); }

    /** Sets the tenant, application, and instance dimensions. */
    default F withApplicationId(Optional<ApplicationId> applicationId) { return applicationId.map(this::with).orElse(self()); }
    /** architecture MUST NOT be 'any'. */
    default F withArchitecture(Optional<Architecture> architecture) { return architecture.map(this::with).orElse(self()); }
    default F withCloudName(Optional<CloudName> cloud) { return cloud.map(this::with).orElse(self()); }
    default F withClusterId(Optional<ClusterSpec.Id> clusterId) { return clusterId.map(this::with).orElse(self()); }
    default F withClusterType(Optional<ClusterSpec.Type> clusterType) { return clusterType.map(this::with).orElse(self()); }
    default F withEnvironment(Optional<Environment> environment) { return environment.map(this::with).orElse(self()); }
    default F withNodeType(Optional<NodeType> nodeType) { return nodeType.map(this::with).orElse(self()); }
    default F withSystemName(Optional<SystemName> systemName) { return systemName.map(this::with).orElse(self()); }
    default F withTenantName(Optional<TenantName> tenantName) { return tenantName.map(this::with).orElse(self()); }
    default F withVersion(Optional<Version> vespaVersion) { return vespaVersion.map(this::with).orElse(self()); }
    default F withZoneId(Optional<ZoneId> zoneId) { return zoneId.map(this::with).orElse(self()); }
    default F withZone(Optional<Zone> zone) { return zone.map(this::with).orElse(self()); }
    default F withZoneApi(Optional<ZoneApi> zoneApi) { return zoneApi.map(this::with).orElse(self()); }

    /** Returns the value, boxed if the flag wraps a primitive type. */
    T boxedValue();
}
