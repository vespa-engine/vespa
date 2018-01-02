package com.yahoo.vespa.hosted.controller.api.integration.zone;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;

import java.util.List;

/**
 * Provides filters for and access to a list of ZoneIds.
 *
 * This is typically offered after an initial filter from {@link ZoneFilter} has been applied.
 * This forces the developer to consider which zones to process.
 *
 * @author jvenstad
 */
public interface ZoneList extends ZoneFilter {

    /** Negates the next filter. */
    @Override
    ZoneList not();

    /** Zones in the given environment. */
    ZoneList in(Environment environment);

    /** Zones in the given region. */
    ZoneList in(RegionName region);

    /** Only the given zones — combine with not() for best effect! */
    ZoneList zones(ZoneId... zones);

    /** Returns the id of all zones in this list as — you guessed it — a list. */
    List<ZoneId> ids();

}
