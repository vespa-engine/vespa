package com.yahoo.vespa.hosted.controller.api.integration.zone;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;

/**
 * A ZoneId list which can be filtered in various ways; elements can be accessed after at least one filter.
 *
 * @author jvenstad
 */
public interface Zones {

    /** Negates the next filter. */
    Zones not();

    /** All zones from the initial pool. */
    Zones.List all();

    /** Zones where which are managed by the controller. */
    Zones.List controllerManaged();

    // TODO: Move to implementation in hosted?
    /** Zones where auto provisioning of nodes is possible. */
    Zones.List autoProvisioned();

    // TODO: Move to implementation in hosted?
    /** Zones where the nodes should be kept track of in an inventory. */
    Zones.List inventoried();

    /** Zones in the given environment. */
    Zones.List in(Environment environment);

    /** Zones in the given region. */
    Zones.List in(RegionName region);


    /** Wraps access to the zones; this forces the user to consider which zones to access. */
    interface List extends Zones {

        /** Returns the id of all zones in this list as — you guessed it — a list. */
        java.util.List<ZoneId> ids();

    }

}
