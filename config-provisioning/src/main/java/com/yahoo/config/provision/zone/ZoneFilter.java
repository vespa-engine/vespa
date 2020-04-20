// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import com.yahoo.config.provision.CloudName;

/**
 * A ZoneId list which can be filtered in various ways; elements can be accessed after at least one filter.
 *
 * The methods here return instances of {@link ZoneList}, which extends ZoneFilter, but with accessors and additional filters.
 * This forces the developer to consider which of the filters in this class to apply, prior to accessing any zones.
 *
 * @author jonmv
 */
public interface ZoneFilter {

    /** Negates the next filter. */
    ZoneFilter not();

    /** Zones which are upgraded by the controller. */
    ZoneList controllerUpgraded();

    /** Zones which support direct routing through exclusive load balancers. */
    ZoneList directlyRouted();

    /** Zones where traffic is routed using given method */
    ZoneList routingMethod(RoutingMethod method);

    /** Zones where config servers are up and running. */
    ZoneList reachable();

    /** All zones from the initial pool. */
    ZoneList all();

    /** Zones in the specified cloud */
    default ZoneList ofCloud(CloudName cloud) {
        return all(); // Not implemented in this repo.
    }

}
