// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RotationName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.provision.lb.DnsZone;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.Real;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Serializer for load balancers.
 *
 * @author mpolden
 */
public class LoadBalancerSerializer {

    private static final String idField = "id";
    private static final String hostnameField = "hostname";
    private static final String dnsZoneField = "dnsZone";
    private static final String inactiveField = "inactive";
    private static final String portsField = "ports";
    private static final String networksField = "networks";
    private static final String realsField = "reals";
    private static final String rotationsField = "rotations";
    private static final String nameField = "name";
    private static final String ipAddressField = "ipAddress";
    private static final String portField = "port";

    public static byte[] toJson(LoadBalancer loadBalancer) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();

        root.setString(idField, loadBalancer.id().serializedForm());
        root.setString(hostnameField, loadBalancer.instance().hostname().toString());
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
        Cursor rotationArray = root.setArray(rotationsField);
        loadBalancer.rotations().forEach(rotation -> {
            Cursor rotationObject = rotationArray.addObject();
            rotationObject.setString(nameField, rotation.value());
        });
        root.setBool(inactiveField, loadBalancer.inactive());

        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static LoadBalancer fromJson(byte[] data) {
        Cursor object = SlimeUtils.jsonToSlime(data).get();

        Set<Real> reals = new LinkedHashSet<>();
        object.field(realsField).traverse((ArrayTraverser) (i, realObject) -> {
            reals.add(new Real(HostName.from(realObject.field(hostnameField).asString()),
                               realObject.field(ipAddressField).asString(),
                               (int) realObject.field(portField).asLong()));

        });

        Set<Integer> ports = new LinkedHashSet<>();
        object.field(portsField).traverse((ArrayTraverser) (i, port) -> ports.add((int) port.asLong()));

        Set<String> networks = new LinkedHashSet<>();
        object.field(networksField).traverse((ArrayTraverser) (i, network) -> networks.add(network.asString()));

        Set<RotationName> rotations = new LinkedHashSet<>();
        object.field(rotationsField).traverse((ArrayTraverser) (i, rotation) -> {
            rotations.add(RotationName.from(rotation.field(nameField).asString()));
        });

        return new LoadBalancer(LoadBalancerId.fromSerializedForm(object.field(idField).asString()),
                                new LoadBalancerInstance(
                                        HostName.from(object.field(hostnameField).asString()),
                                        optionalField(object.field(dnsZoneField), DnsZone::new),
                                        ports,
                                        networks,
                                        reals
                                ),
                                rotations,
                                object.field(inactiveField).asBool());
    }

    private static <T> Optional<T> optionalField(Inspector field, Function<String, T> fieldMapper) {
        return Optional.of(field).filter(Inspector::valid).map(Inspector::asString).map(fieldMapper);
    }

}
