// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import ai.vespa.http.DomainName;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicyId;
import com.yahoo.vespa.hosted.controller.routing.RoutingStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private static final String ipAddressField = "ipAddress";
    private static final String zoneField = "zone";
    private static final String dnsZoneField = "dnsZone";
    private static final String instanceEndpointsField = "rotations";
    private static final String applicationEndpointsField = "applicationEndpoints";
    private static final String loadBalancerActiveField = "active";
    private static final String globalRoutingField = "globalRouting";
    private static final String agentField = "agent";
    private static final String changedAtField = "changedAt";
    private static final String statusField = "status";
    private static final String privateOnlyField = "private";

    public Slime toSlime(List<RoutingPolicy> routingPolicies) {
        var slime = new Slime();
        var root = slime.setObject();
        var policyArray = root.setArray(routingPoliciesField);
        routingPolicies.forEach(policy -> {
            var policyObject = policyArray.addObject();
            policyObject.setString(clusterField, policy.id().cluster().value());
            policyObject.setString(zoneField, policy.id().zone().value());
            policy.canonicalName().map(DomainName::value).ifPresent(name -> policyObject.setString(canonicalNameField, name));
            policy.ipAddress().ifPresent(ipAddress -> policyObject.setString(ipAddressField, ipAddress));
            policy.dnsZone().ifPresent(dnsZone -> policyObject.setString(dnsZoneField, dnsZone));
            var instanceEndpointsArray = policyObject.setArray(instanceEndpointsField);
            policy.instanceEndpoints().forEach(endpointId -> instanceEndpointsArray.addString(endpointId.id()));
            var applicationEndpointsArray = policyObject.setArray(applicationEndpointsField);
            policy.applicationEndpoints().forEach(endpointId -> applicationEndpointsArray.addString(endpointId.id()));
            globalRoutingToSlime(policy.routingStatus(), policyObject.setObject(globalRoutingField));
            if ( ! policy.isPublic()) policyObject.setBool(privateOnlyField, true);
        });
        return slime;
    }

    public List<RoutingPolicy> fromSlime(ApplicationId owner, Slime slime) {
        List<RoutingPolicy> policies = new ArrayList<>();
        var root = slime.get();
        var field = root.field(routingPoliciesField);
        field.traverse((ArrayTraverser) (i, inspect) -> {
            Set<EndpointId> instanceEndpoints = new LinkedHashSet<>();
            inspect.field(instanceEndpointsField).traverse((ArrayTraverser) (j, endpointId) -> instanceEndpoints.add(EndpointId.of(endpointId.asString())));
            Set<EndpointId> applicationEndpoints = new LinkedHashSet<>();
            inspect.field(applicationEndpointsField).traverse((ArrayTraverser) (idx, endpointId) -> applicationEndpoints.add(EndpointId.of(endpointId.asString())));
            RoutingPolicyId id = new RoutingPolicyId(owner,
                                                     ClusterSpec.Id.from(inspect.field(clusterField).asString()),
                                                     ZoneId.from(inspect.field(zoneField).asString()));
            boolean isPublic = ! inspect.field(privateOnlyField).asBool();
            policies.add(new RoutingPolicy(id,
                                           SlimeUtils.optionalString(inspect.field(canonicalNameField)).map(DomainName::of),
                                           SlimeUtils.optionalString(inspect.field(ipAddressField)),
                                           SlimeUtils.optionalString(inspect.field(dnsZoneField)),
                                           instanceEndpoints,
                                           applicationEndpoints,
                                           routingStatusFromSlime(inspect.field(globalRoutingField)),
                                           isPublic));
        });
        return Collections.unmodifiableList(policies);
    }

    public void globalRoutingToSlime(RoutingStatus routingStatus, Cursor object) {
        object.setString(statusField, routingStatus.value().name());
        object.setString(agentField, routingStatus.agent().name());
        object.setLong(changedAtField, routingStatus.changedAt().toEpochMilli());
    }

    public RoutingStatus routingStatusFromSlime(Inspector object) {
        var status = RoutingStatus.Value.valueOf(object.field(statusField).asString());
        var agent = RoutingStatus.Agent.valueOf(object.field(agentField).asString());
        var changedAt = SlimeUtils.optionalInstant(object.field(changedAtField)).orElse(Instant.EPOCH);
        return new RoutingStatus(status, agent, changedAt);
    }

}
