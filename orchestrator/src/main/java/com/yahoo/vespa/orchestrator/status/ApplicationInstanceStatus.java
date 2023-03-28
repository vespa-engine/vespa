// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

/**
 * Enumeration of the orchestrator status on the application level.
 *
 * The naming and conventions follows the same pattern as with the HostStatus and is:
 *
 * When the node is suspended - the orchestration state is 'allowed to be down'. The application
 * is not necessarily suspended pr. se, but it is allowed to start suspending - or is back up from suspension
 * but the flag is not revoked yet.
 *
 * @see HostStatus
 * @author andreer
 * @author smorgrav
 */
public enum ApplicationInstanceStatus {
    NO_REMARKS,
    ALLOWED_TO_BE_DOWN
}
