// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.common.collect.ImmutableSet;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The hosts allocated to an application.
 * This can be serialized to/from JSON.
 * This is immutable.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public class AllocatedHosts {

    private static final String mappingKey = "mapping";
    private static final String hostSpecKey = "hostSpec";
    private static final String hostSpecHostName = "hostName";
    private static final String hostSpecMembership = "membership";
    private static final String hostSpecFlavor = "flavor";

    /** Wanted version */
    private static final String hostSpecVespaVersion = "vespaVersion";

    /** Current version */
    private static final String hostSpecCurrentVespaVersion = "currentVespaVersion";

    private final ImmutableSet<HostSpec> hosts;

    AllocatedHosts(Set<HostSpec> hosts) {
        this.hosts = ImmutableSet.copyOf(hosts);
    }

    public static AllocatedHosts withHosts(Set<HostSpec> hosts) {
        return new AllocatedHosts(hosts);
    }

    private void toSlime(Cursor cursor) {
        Cursor array = cursor.setArray(mappingKey);
        for (HostSpec host : hosts)
            toSlime(host, array.addObject().setObject(hostSpecKey));
    }

    private void toSlime(HostSpec host, Cursor cursor) {
        cursor.setString(hostSpecHostName, host.hostname());
        host.membership().ifPresent(membership -> {
            cursor.setString(hostSpecMembership, membership.stringValue());
            cursor.setString(hostSpecVespaVersion, membership.cluster().vespaVersion().toString());
        });
        host.flavor().ifPresent(flavor -> cursor.setString(hostSpecFlavor, flavor.name()));
        host.version().ifPresent(version -> cursor.setString(hostSpecCurrentVespaVersion, version.toString()));
    }

    /** Returns the hosts of this allocation */
    public Set<HostSpec> getHosts() { return hosts; }

    private static AllocatedHosts fromSlime(Inspector inspector, Optional<NodeFlavors> nodeFlavors) {
        Inspector array = inspector.field(mappingKey);
        Set<HostSpec> hosts = new LinkedHashSet<>();
        array.traverse(new ArrayTraverser() {
            @Override
            public void entry(int i, Inspector inspector) {
                hosts.add(hostFromSlime(inspector.field(hostSpecKey), nodeFlavors));
            }
        });
        return new AllocatedHosts(hosts);
    }

    static HostSpec hostFromSlime(Inspector object, Optional<NodeFlavors> nodeFlavors) {
        Optional<ClusterMembership> membership =
                object.field(hostSpecMembership).valid() ? Optional.of(membershipFromSlime(object)) : Optional.empty();
        Optional<Flavor> flavor =
                object.field(hostSpecFlavor).valid() ? flavorFromSlime(object, nodeFlavors) : Optional.empty();
        Optional<com.yahoo.component.Version> version =
                optionalString(object.field(hostSpecCurrentVespaVersion)).map(com.yahoo.component.Version::new);

        return new HostSpec(object.field(hostSpecHostName).asString(), Collections.emptyList(), flavor, membership, version);
    }

    private static ClusterMembership membershipFromSlime(Inspector object) {
        return ClusterMembership.from(object.field(hostSpecMembership).asString(),
                                      com.yahoo.component.Version.fromString(object.field(hostSpecVespaVersion).asString()));
    }

    private static Optional<Flavor> flavorFromSlime(Inspector object, Optional<NodeFlavors> nodeFlavors) {
        return nodeFlavors.map(flavorMapper ->  flavorMapper.getFlavor(object.field(hostSpecFlavor).asString()))
                .orElse(Optional.empty());
    }

    private static Optional<String> optionalString(Inspector inspector) {
        if ( ! inspector.valid()) return Optional.empty();
        return Optional.of(inspector.asString());
    }

    public byte[] toJson() throws IOException {
        Slime slime = new Slime();
        toSlime(slime.setObject());
        return SlimeUtils.toJsonBytes(slime);
    }

    public static AllocatedHosts fromJson(byte[] json, Optional<NodeFlavors> nodeFlavors) {
        return fromSlime(SlimeUtils.jsonToSlime(json).get(), nodeFlavors);
    }
    
    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof AllocatedHosts)) return false;
        return ((AllocatedHosts) other).hosts.equals(this.hosts);
    }
    
    @Override
    public int hashCode() {
        return hosts.hashCode();
    }

}
