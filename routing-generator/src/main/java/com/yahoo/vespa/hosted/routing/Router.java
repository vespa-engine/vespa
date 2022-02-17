// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing;

/**
 * A {@link Router} (e.g. a reverse proxy) consumes a {@link RoutingTable} by
 * translating it to the router's own format and loading it.
 *
 * @author mpolden
 */
public interface Router {

    /** Load the given routing table */
    void load(RoutingTable table);

}
