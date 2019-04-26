// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RotationName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Serializer and deserializer for a {@link RoutingPolicy}.
 *
 * @author mortent
 * @author mpolden
 */
public class RoutingPolicySerializer {

    private static final String routingPoliciesField = "routingPolicies";
    private static final String clusterField = "cluster";
    private static final String canonicalNameField = "canonicalName";
    private static final String zoneField = "zone";
    private static final String dnsZoneField = "dnsZone";
    private static final String rotationsField = "rotations";

    public Slime toSlime(Set<RoutingPolicy> routingPolicies) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor policyArray = root.setArray(routingPoliciesField);
        routingPolicies.forEach(policy -> {
            Cursor policyObject = policyArray.addObject();
            policyObject.setString(clusterField, policy.cluster().value());
            policyObject.setString(zoneField, policy.zone().value());
            policyObject.setString(canonicalNameField, policy.canonicalName().value());
            policy.dnsZone().ifPresent(dnsZone -> policyObject.setString(dnsZoneField, dnsZone));
            Cursor rotationArray = policyObject.setArray(rotationsField);
            policy.rotations().forEach(rotation -> {
                rotationArray.addString(rotation.value());
            });
        });
        return slime;
    }

    public Set<RoutingPolicy> fromSlime(ApplicationId owner, Slime slime) {
        Set<RoutingPolicy> policies = new LinkedHashSet<>();
        Cursor root = slime.get();
        Cursor field = root.field(routingPoliciesField);
        field.traverse((ArrayTraverser) (i, inspect) -> {
            Set<RotationName> rotations = new LinkedHashSet<>();
            inspect.field(rotationsField).traverse((ArrayTraverser) (j, rotation) -> rotations.add(RotationName.from(rotation.asString())));
            policies.add(new RoutingPolicy(owner,
                                           clusterId(inspect.field(clusterField)),
                                           ZoneId.from(inspect.field(zoneField).asString()),
                                           HostName.from(inspect.field(canonicalNameField).asString()),
                                           Serializers.optionalField(inspect.field(dnsZoneField), Function.identity()),
                                           rotations));
        });
        return Collections.unmodifiableSet(policies);
    }

    // TODO: Remove and inline after Vespa 7.43
    private static ClusterSpec.Id clusterId(Inspector field) {
        return Serializers.optionalField(field, ClusterSpec.Id::from).orElseGet(() -> new ClusterSpec.Id("default"));
    }


}
