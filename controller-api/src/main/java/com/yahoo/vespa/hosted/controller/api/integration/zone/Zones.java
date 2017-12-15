package com.yahoo.vespa.hosted.controller.api.integration.zone;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;

/**
 * A ZoneId list which can be filtered in various ways; elements can be accessed after at least one filter.
 *
 * The class is split in an outer and an inner class to force the user to make an initial choice between
 * the filters available in the outer class; these should be pertinent to what code should use the zone,
 * while the filters in the inner class are optional and / or for convenience.
 *
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


    /** Wraps access to the zones; this forces the user to consider which zones to access. */
    interface List extends Zones {

        /** Negates the next filter. */
        @Override
        Zones.List not();

        /** Zones in the given environment. */
        Zones.List in(Environment environment);

        /** Zones in the given region. */
        Zones.List in(RegionName region);

        /** Returns the id of all zones in this list as — you guessed it — a list. */
        java.util.List<ZoneId> ids();

    }

}
