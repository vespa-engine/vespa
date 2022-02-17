// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.status;

/**
 * Interface for accessing the global routing status of an upstream server.
 *
* @author oyving
*/
public interface RoutingStatus {

    /** Returns whether the given upstream name is active in global routing */
    boolean isActive(String upstreamName);

}
