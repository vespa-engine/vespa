// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.component.Version;
import com.yahoo.messagebus.metrics.MetricSet;
import com.yahoo.messagebus.routing.RoutingPolicy;

/**
 * Interface implemented by the concrete application message protocol.
 *
 * @author bratseth
 * @author Simon Thoresen Hult
 */
public interface Protocol {

    /**
     * Returns a global unique name for this protocol.
     *
     * @return The name.
     */
    public String getName();

    /**
     * Encodes the protocol specific data of a routable into a byte array.
     *
     * @param version  The version to encode for.
     * @param routable The routable to encode.
     * @return The encoded data.
     */
    public byte[] encode(Version version, Routable routable);

    /**
     * Decodes the protocol specific data into a routable of the correct type.
     *
     * @param version The version of the serialized routable.
     * @param payload The payload to decode from.
     * @return The decoded routable.
     */
    public Routable decode(Version version, byte[] payload);

    /**
     * Create a policy of the named type with the named param passed to the constructor of that policy.
     *
     * @param name  The name of the policy to create.
     * @param param The parameter to that policy's constructor.
     * @return The created policy.
     */
    public RoutingPolicy createPolicy(String name, String param);

    /**
     * Returns the metrics associated with this protocol.
     */
    MetricSet getMetrics();
}
