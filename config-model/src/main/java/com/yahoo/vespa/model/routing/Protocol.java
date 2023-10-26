// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.routing;

import com.yahoo.messagebus.routing.ApplicationSpec;
import com.yahoo.messagebus.routing.RoutingTableSpec;

/**
 * This interface defines the necessary api for {@link Routing} to prepare and combine routing tables for all available
 * protocols.
 *
 * @author Simon Thoresen Hult
 */
public interface Protocol {

    /** Returns the specification for the routing table of this protocol. */
    RoutingTableSpec getRoutingTableSpec();

    /** Returns the specification of the application as seen by this protocol. */
    ApplicationSpec getApplicationSpec();

}
