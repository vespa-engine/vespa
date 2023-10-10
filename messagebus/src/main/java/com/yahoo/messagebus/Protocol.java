// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.component.Version;
import com.yahoo.messagebus.routing.RoutingPolicy;

/**
 * Interface implemented by the concrete application message protocol.
 *
 * @author bratseth
 * @author Simon Thoresen Hult
 */
public interface Protocol {

    /** Returns a global unique name for this protocol. */
    String getName();

    /**
     * Encodes the protocol specific data of a routable into a byte array.
     *
     * @param version  the version to encode for
     * @param routable the routable to encode
     * @return the encoded data
     */
    byte[] encode(Version version, Routable routable);

    /**
     * Decodes the protocol specific data into a routable of the correct type.
     *
     * @param version the version of the serialized routable
     * @param payload the payload to decode from
     * @return the decoded routable, or null if it could not be decoded
     */
    Routable decode(Version version, byte[] payload);

    /**
     * Create a policy of the named type with the named param passed to the constructor of that policy.
     *
     * @param name  the name of the policy to create
     * @param param the parameter to that policy's constructor
     * @return the created policy
     */
    RoutingPolicy createPolicy(String name, String param);

}
