// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.systemflags.v1;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.flags.json.FlagData;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget.defaultFile;
import static com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget.environmentFile;
import static com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget.systemFile;
import static com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget.zoneFile;

/**
 * @author bjorncs
 */
class ConfigServerFlagsTarget implements FlagsTarget {
    private final SystemName system;
    private final CloudName cloud;
    private final ZoneId zone;
    private final URI endpoint;
    private final AthenzIdentity identity;

    ConfigServerFlagsTarget(SystemName system, CloudName cloud, ZoneId zone, URI endpoint, AthenzIdentity identity) {
        this.system = Objects.requireNonNull(system);
        this.cloud = Objects.requireNonNull(cloud);
        this.zone = Objects.requireNonNull(zone);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.identity = Objects.requireNonNull(identity);
    }

    @Override public List<String> flagDataFilesPrioritized() { return List.of(zoneFile(system, zone), environmentFile(system, zone.environment()), systemFile(system), defaultFile()); }
    @Override public URI endpoint() { return endpoint; }
    @Override public Optional<AthenzIdentity> athenzHttpsIdentity() { return Optional.of(identity); }
    @Override public String asString() { return String.format("%s.%s", system.value(), zone.value()); }

    @Override
    public FlagData partiallyResolveFlagData(FlagData data) {
        return FlagsTarget.partialResolve(data, system, cloud, zone);
    }

    @Override
    public String toString() {
        return "ConfigServerFlagsTarget{" +
               "system=" + system +
               ", cloud=" + cloud +
               ", zone=" + zone +
               ", endpoint=" + endpoint +
               ", identity=" + identity +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigServerFlagsTarget that = (ConfigServerFlagsTarget) o;
        return system == that.system && cloud.equals(that.cloud) && zone.equals(that.zone) && endpoint.equals(that.endpoint) && identity.equals(that.identity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(system, cloud, zone, endpoint, identity);
    }
}
