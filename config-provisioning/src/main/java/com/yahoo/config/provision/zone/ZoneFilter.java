// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

/**
 * A ZoneId list which can be filtered in various ways; elements can be accessed after at least one filter.
 *
 * The methods here return instances of {@link ZoneList}, which extends ZoneFilter, but with accessors and additional filters.
 * This forces the developer to consider which of the filters in this class to apply, prior to accessing any zones.
 * Note: Do not add further filters, as this is only meant for the levels of configuration of the zone, not other properties.
 *
 * @author jonmv
 */
public interface ZoneFilter {

    /** Negates the next filter. */
    ZoneFilter not();

    /** All zones defined in code, including those not yet set up. */
    ZoneList all();

    /** Zones where config servers are up and running. */
    ZoneList reachable();

    /** Zones which are upgraded by the controller. */
    ZoneList controllerUpgraded();

    /** Zones for use by tenants. */
    ZoneList publiclyVisible();

}
