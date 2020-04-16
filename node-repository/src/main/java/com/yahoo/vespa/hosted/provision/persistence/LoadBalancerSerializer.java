// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.HostName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.lb.DnsZone;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.Real;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.function.Function;

/**
 * Serializer for load balancers.
 *
 * @author mpolden
 */
public class LoadBalancerSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String idField = "id";
    private static final String hostnameField = "hostname";
    private static final String stateField = "state";
    private static final String changedAtField = "changedAt";
    private static final String dnsZoneField = "dnsZone";
    private static final String portsField = "ports";
    private static final String networksField = "networks";
    private static final String realsField = "reals";
    private static final String ipAddressField = "ipAddress";
    private static final String portField = "port";

    public static byte[] toJson(LoadBalancer loadBalancer) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();

        root.setString(idField, loadBalancer.id().serializedForm());
        root.setString(hostnameField, loadBalancer.instance().hostname().toString());
        root.setString(stateField, asString(loadBalancer.state()));
        root.setLong(changedAtField, loadBalancer.changedAt().toEpochMilli());
        loadBalancer.instance().dnsZone().ifPresent(dnsZone -> root.setString(dnsZoneField, dnsZone.id()));
        Cursor portArray = root.setArray(portsField);
        loadBalancer.instance().ports().forEach(portArray::addLong);
        Cursor networkArray = root.setArray(networksField);
        loadBalancer.instance().networks().forEach(networkArray::addString);
        Cursor realArray = root.setArray(realsField);
        loadBalancer.instance().reals().forEach(real -> {
            Cursor realObject = realArray.addObject();
            realObject.setString(hostnameField, real.hostname().value());
            realObject.setString(ipAddressField, real.ipAddress());
            realObject.setLong(portField, real.port());
        });
        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static LoadBalancer fromJson(byte[] data) {
        Cursor object = SlimeUtils.jsonToSlime(data).get();

        var reals = new LinkedHashSet<Real>();
        object.field(realsField).traverse((ArrayTraverser) (i, realObject) -> {
            reals.add(new Real(HostName.from(realObject.field(hostnameField).asString()),
                               realObject.field(ipAddressField).asString(),
                               (int) realObject.field(portField).asLong()));

        });

        var ports = new LinkedHashSet<Integer>();
        object.field(portsField).traverse((ArrayTraverser) (i, port) -> ports.add((int) port.asLong()));

        var networks = new LinkedHashSet<String>();
        object.field(networksField).traverse((ArrayTraverser) (i, network) -> networks.add(network.asString()));

        return new LoadBalancer(LoadBalancerId.fromSerializedForm(object.field(idField).asString()),
                                new LoadBalancerInstance(
                                        HostName.from(object.field(hostnameField).asString()),
                                        optionalString(object.field(dnsZoneField), DnsZone::new),
                                        ports,
                                        networks,
                                        reals
                                ),
                                stateFromString(object.field(stateField).asString()),
                                Instant.ofEpochMilli(object.field(changedAtField).asLong()));
    }

    private static <T> Optional<T> optionalValue(Inspector field, Function<Inspector, T> fieldMapper) {
        return Optional.of(field).filter(Inspector::valid).map(fieldMapper);
    }

    private static <T> Optional<T> optionalString(Inspector field, Function<String, T> fieldMapper) {
        return optionalValue(field, Inspector::asString).map(fieldMapper);
    }

    private static String asString(LoadBalancer.State state) {
        switch (state) {
            case active: return "active";
            case inactive: return "inactive";
            case reserved: return "reserved";
            default: throw new IllegalArgumentException("No serialization defined for state enum '" + state + "'");
        }
    }

    private static LoadBalancer.State stateFromString(String state) {
        switch (state) {
            case "active": return LoadBalancer.State.active;
            case "inactive": return LoadBalancer.State.inactive;
            case "reserved": return LoadBalancer.State.reserved;
            default: throw new IllegalArgumentException("No serialization defined for state string '" + state + "'");
        }
    }

}
