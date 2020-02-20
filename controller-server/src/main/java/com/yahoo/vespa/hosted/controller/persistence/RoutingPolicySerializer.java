// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.routing.GlobalRouting;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicyId;
import com.yahoo.vespa.hosted.controller.routing.Status;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Serializer and deserializer for a {@link RoutingPolicy}.
 *
 * @author mortent
 * @author mpolden
 */
public class RoutingPolicySerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String routingPoliciesField = "routingPolicies";
    private static final String clusterField = "cluster";
    private static final String canonicalNameField = "canonicalName";
    private static final String zoneField = "zone";
    private static final String dnsZoneField = "dnsZone";
    private static final String rotationsField = "rotations";
    private static final String loadBalancerActiveField = "active";
    private static final String globalRoutingField = "globalRouting";
    private static final String agentField = "agent";
    private static final String changedAtField = "changedAt";
    private static final String statusField = "status";

    public Slime toSlime(Map<RoutingPolicyId, RoutingPolicy> routingPolicies) {
        var slime = new Slime();
        var root = slime.setObject();
        var policyArray = root.setArray(routingPoliciesField);
        routingPolicies.values().forEach(policy -> {
            var policyObject = policyArray.addObject();
            policyObject.setString(clusterField, policy.id().cluster().value());
            policyObject.setString(zoneField, policy.id().zone().value());
            policyObject.setString(canonicalNameField, policy.canonicalName().value());
            policy.dnsZone().ifPresent(dnsZone -> policyObject.setString(dnsZoneField, dnsZone));
            var rotationArray = policyObject.setArray(rotationsField);
            policy.endpoints().forEach(endpointId -> {
                rotationArray.addString(endpointId.id());
            });
            policyObject.setBool(loadBalancerActiveField, policy.status().isActive());
            globalRoutingToSlime(policy.status().globalRouting(), policyObject.setObject(globalRoutingField));
        });
        return slime;
    }

    public Map<RoutingPolicyId, RoutingPolicy> fromSlime(ApplicationId owner, Slime slime) {
        var policies = new LinkedHashMap<RoutingPolicyId, RoutingPolicy>();
        var root = slime.get();
        var field = root.field(routingPoliciesField);
        field.traverse((ArrayTraverser) (i, inspect) -> {
            var endpointIds = new LinkedHashSet<EndpointId>();
            inspect.field(rotationsField).traverse((ArrayTraverser) (j, endpointId) -> endpointIds.add(EndpointId.of(endpointId.asString())));
            var id = new RoutingPolicyId(owner,
                                         ClusterSpec.Id.from(inspect.field(clusterField).asString()),
                                         ZoneId.from(inspect.field(zoneField).asString()));
            policies.put(id, new RoutingPolicy(id,
                                               HostName.from(inspect.field(canonicalNameField).asString()),
                                               Serializers.optionalString(inspect.field(dnsZoneField)),
                                               endpointIds,
                                               new Status(inspect.field(loadBalancerActiveField).asBool(),
                                                          globalRoutingFromSlime(inspect.field(globalRoutingField)))));
        });
        return Collections.unmodifiableMap(policies);
    }

    public void globalRoutingToSlime(GlobalRouting globalRouting, Cursor object) {
        object.setString(statusField, globalRouting.status().name());
        object.setString(agentField, globalRouting.agent().name());
        object.setLong(changedAtField, globalRouting.changedAt().toEpochMilli());
    }

    public GlobalRouting globalRoutingFromSlime(Inspector object) {
        if (!object.valid()) return GlobalRouting.DEFAULT_STATUS;
        var status = GlobalRouting.Status.valueOf(object.field(statusField).asString());
        var agent = GlobalRouting.Agent.valueOf(object.field(agentField).asString());
        var changedAt = Serializers.optionalInstant(object.field(changedAtField)).orElse(Instant.EPOCH);
        return new GlobalRouting(status, agent, changedAt);
    }

}
