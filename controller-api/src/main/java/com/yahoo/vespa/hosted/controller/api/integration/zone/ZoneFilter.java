// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.zone;

/**
 * A ZoneId list which can be filtered in various ways; elements can be accessed after at least one filter.
 *
 * The methods here return instances of {@link ZoneList}, which extends ZoneFilter, but with accessors and additional filters.
 * This forces the developer to consider which of the filters in this class to apply, prior to processing any zones.
 *
 * @author jvenstad
 */
public interface ZoneFilter {

    /** Negates the next filter. */
    ZoneFilter not();

    /** All zones from the initial pool. */
    ZoneList all();

    /** Zones which are upgraded by the controller. */
    ZoneList controllerUpgraded();

    /** Zones where config servers are up and running. */
    ZoneList reachable();

}
