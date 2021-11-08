// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.routing.ZoneRoutingPolicy;

import java.util.Objects;

/**
 * Serializer for {@link ZoneRoutingPolicy}.
 *
 * @author mpolden
 */
public class ZoneRoutingPolicySerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String GLOBAL_ROUTING_FIELD = "globalRouting";

    private final RoutingPolicySerializer routingPolicySerializer;

    public ZoneRoutingPolicySerializer(RoutingPolicySerializer routingPolicySerializer) {
        this.routingPolicySerializer = Objects.requireNonNull(routingPolicySerializer, "routingPolicySerializer must be non-null");
    }

    public ZoneRoutingPolicy fromSlime(ZoneId zone, Slime slime) {
        var root = slime.get();
        return new ZoneRoutingPolicy(zone, routingPolicySerializer.globalRoutingFromSlime(root.field(GLOBAL_ROUTING_FIELD)));
    }

    public Slime toSlime(ZoneRoutingPolicy policy) {
        var slime = new Slime();
        var root = slime.setObject();
        routingPolicySerializer.globalRoutingToSlime(policy.routingStatus(), root.setObject(GLOBAL_ROUTING_FIELD));
        return slime;
    }

}
