// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.response;

/**
 * The response of a set operation.
 *
 * @author Haakon Dybdahl
 */
public class SetResponse {

    private final String reason;
    private final boolean wasModified;

    public SetResponse(String reason, boolean wasModified) {
        this.reason = reason;
        this.wasModified = wasModified;
    }

    /**
     * Indicates if data was modified in a set operation.
     *
     * @return true if modified.
     */
    public boolean getWasModified() { return wasModified; }

    /**
     * Human-readable reason.
     *
     * @return reason as string
     */
    public String getReason() { return reason; }

}
