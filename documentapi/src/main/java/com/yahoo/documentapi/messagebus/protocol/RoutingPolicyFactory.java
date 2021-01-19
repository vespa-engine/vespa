// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.messagebus.routing.RoutingPolicy;

/**
 * This interface defines the necessary methods of a routing policy factory that can be plugged into a {@link
 * DocumentProtocol} using the {@link DocumentProtocol#putRoutingPolicyFactory(String, RoutingPolicyFactory)} method.
 *
 * @author Simon Thoresen Hult
 */
public interface RoutingPolicyFactory {

    /**
     * This method creates and returns a routing policy that corresponds to the implementing class, using the given
     * parameter string. There is only ever one instance of a routing policy for a given name and parameter combination,
     * and because of this the policies must be stateless beyond what can be derived from the parameter string. Because
     * there is only a single thread running route resolution within message bus, it is not necessary to make policies
     * thread-safe. For more information see {@link RoutingPolicy}.
     *
     * Do NOT throw exceptions out of this method because that will cause the running thread to die, just return null to
     * signal failure instead.
     *
     * @param param The parameter to use when creating the policy.
     * @return The created routing policy.
     */
    DocumentProtocolRoutingPolicy createPolicy(String param);

}
