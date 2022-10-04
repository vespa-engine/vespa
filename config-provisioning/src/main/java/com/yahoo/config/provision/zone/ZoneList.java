// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides filters for and access to a list of ZoneIds.
 *
 * This is typically offered after an initial filter from {@link ZoneFilter} has been applied.
 * This forces the developer to consider which zones to process.
 *
 * @author jonmv
 */
public interface ZoneList extends ZoneFilter {

    /** Negates the next filter. */
    @Override
    ZoneList not();

    /** Zones in one of the given environments. */
    ZoneList in(Environment... environments);

    /** Zones in one of the given regions. */
    ZoneList in(RegionName... regions);

    /** Zones in one of the given clouds. */
    ZoneList in(CloudName... clouds);

    /** Only the given zones — combine with not() for best effect! */
    ZoneList among(ZoneId... zones);

    /** Zones where hosts are dynamically provisioned */
    ZoneList dynamicallyProvisioned();

    /** Zones where traffic is routed using given method */
    ZoneList routingMethod(RoutingMethod method);

    /** Returns the zone with the given id, if this exists. */
    default Optional<? extends ZoneApi> get(ZoneId id) {
        return among(id).zones().stream().findFirst();
    }

    /** Returns the ZoneApi of all zones in this list. */
    List<? extends ZoneApi> zones();

    /** Returns the ZoneIds of all zones in this list. */
    default List<ZoneId> ids() {
        return zones().stream().map(ZoneApi::getVirtualId).collect(Collectors.toList());
    }

}
