// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

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
 * Information about hosts provisioned for an application, and (de)serialization of this information to/from JSON.
 *
 * @author lulf
 * @since 5.12
 */
public class ProvisionInfo {

    private static final String mappingKey = "mapping";
    private static final String hostSpecKey = "hostSpec";
    private static final String hostSpecHostName = "hostName";
    private static final String hostSpecMembership = "membership";
    private static final String hostSpecFlavor = "flavor";
    private static final String hostSpecVespaVersion = "vespaVersion";

    private final Set<HostSpec> hosts = new LinkedHashSet<>();

    private ProvisionInfo(Set<HostSpec> hosts) {
        this.hosts.addAll(hosts);
    }

    public static ProvisionInfo withHosts(Set<HostSpec> hosts) {
        return new ProvisionInfo(hosts);
    }

    private void toSlime(Cursor cursor) {
        Cursor array = cursor.setArray(mappingKey);
        for (HostSpec host : hosts) {
            Cursor object = array.addObject();
            serializeHostSpec(object.setObject(hostSpecKey), host);
        }
    }

    private void serializeHostSpec(Cursor cursor, HostSpec host) {
        cursor.setString(hostSpecHostName, host.hostname());
        if (host.membership().isPresent()) {
            cursor.setString(hostSpecMembership, host.membership().get().stringValue());
            cursor.setString(hostSpecVespaVersion, host.membership().get().cluster().vespaVersion().toString());
        }
        if (host.flavor().isPresent())
            cursor.setString(hostSpecFlavor, host.flavor().get().name());
    }

    public Set<HostSpec> getHosts() {
        return Collections.unmodifiableSet(hosts);
    }

    private static ProvisionInfo fromSlime(Inspector inspector, Optional<NodeFlavors> nodeFlavors) {
        Inspector array = inspector.field(mappingKey);
        Set<HostSpec> hosts = new LinkedHashSet<>();
        array.traverse(new ArrayTraverser() {
            @Override
            public void entry(int i, Inspector inspector) {
                hosts.add(deserializeHostSpec(inspector.field(hostSpecKey), nodeFlavors));
            }
        });
        return new ProvisionInfo(hosts);
    }

    private static HostSpec deserializeHostSpec(Inspector object, Optional<NodeFlavors> nodeFlavors) {
        Optional<ClusterMembership> membership =
                object.field(hostSpecMembership).valid() ? Optional.of(readMembership(object)) : Optional.empty();
        Optional<Flavor> flavor =
                object.field(hostSpecFlavor).valid() ? readFlavor(object, nodeFlavors) : Optional.empty();

        return new HostSpec(object.field(hostSpecHostName).asString(),Collections.emptyList(), flavor, membership);
    }

    private static ClusterMembership readMembership(Inspector object) {
        return ClusterMembership.from(object.field(hostSpecMembership).asString(),
                                      com.yahoo.component.Version.fromString(object.field(hostSpecVespaVersion).asString()));
    }

    private static Optional<Flavor> readFlavor(Inspector object, Optional<NodeFlavors> nodeFlavors) {
        return nodeFlavors.map(flavorMapper ->  flavorMapper.getFlavor(object.field(hostSpecFlavor).asString()))
                .orElse(Optional.empty());
    }

    public byte[] toJson() throws IOException {
        Slime slime = new Slime();
        toSlime(slime.setObject());
        return SlimeUtils.toJsonBytes(slime);
    }

    public static ProvisionInfo fromJson(byte[] json, Optional<NodeFlavors> nodeFlavors) {
        return fromSlime(SlimeUtils.jsonToSlime(json).get(), nodeFlavors);
    }

    public ProvisionInfo merge(ProvisionInfo provisionInfo) {
        Set<HostSpec> mergedSet = new LinkedHashSet<>();
        mergedSet.addAll(this.hosts);
        mergedSet.addAll(provisionInfo.getHosts());
        return ProvisionInfo.withHosts(mergedSet);
    }

}
