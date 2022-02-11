// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.status;

/**
 * Interface for accessing the health status of servers behind a router/reverse proxy.
 *
* @author oyving
*/
// TODO(mpolden): Make this a part of the future Router interface
public interface HealthStatus {

    /** Returns status of all servers */
    ServerGroup servers();

}
