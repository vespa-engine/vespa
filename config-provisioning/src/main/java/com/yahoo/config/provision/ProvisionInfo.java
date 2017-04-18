// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Information about provisioned hosts, and (de)serialization (from)to JSON.
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
    private static final String dockerImage = "dockerImage";

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
            if (host.membership().get().cluster().dockerImage().isPresent())
                cursor.setString(dockerImage, host.membership().get().cluster().dockerImage().get());
        }
        if (host.flavor().isPresent())
            cursor.setString(hostSpecFlavor, host.flavor().get().name());
    }

    public Set<HostSpec> getHosts() {
        return Collections.unmodifiableSet(hosts);
    }

    private static ProvisionInfo fromSlime(Inspector inspector, Optional<NodeFlavors> nodeFlavors) {
        Inspector array = inspector.field(mappingKey);
        final Set<HostSpec> hosts = new LinkedHashSet<>();
        array.traverse(new ArrayTraverser() {
            @Override
            public void entry(int i, Inspector inspector) {
                hosts.add(createHostSpec(inspector.field(hostSpecKey), nodeFlavors));
            }
        });
        return new ProvisionInfo(hosts);
    }

    private static HostSpec createHostSpec(Inspector object, Optional<NodeFlavors> nodeFlavors) {
        Optional<ClusterMembership> membership =
                object.field(hostSpecMembership).valid() ? Optional.of(readMembership(object)) : Optional.empty();
        Optional<Flavor> flavor =
                object.field(hostSpecFlavor).valid() ? readFlavor(object, nodeFlavors) : Optional.empty();

        return new HostSpec(object.field(hostSpecHostName).asString(),Collections.emptyList(), flavor, membership);
    }

    private static ClusterMembership readMembership(Inspector object) {
        return ClusterMembership.from(object.field(hostSpecMembership).asString(),
                                      object.field(dockerImage).valid() ? Optional.of(object.field(dockerImage).asString()) : Optional.empty());
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
