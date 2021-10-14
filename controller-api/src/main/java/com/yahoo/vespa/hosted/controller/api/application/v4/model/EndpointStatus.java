// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

/**
 * Represent the operational status of a service endpoint (where the endpoint itself
 * is identified by the container cluster id).
 *
 * The status of an endpoint may be assigned from the controller.
 *
 * @author smorgrav
 */
public class EndpointStatus {
    private final String agent;
    private final String reason;
    private final Status status;
    private final long epoch;

    public enum Status {
        in,
        out,
        unknown;
    }

    public EndpointStatus(Status status, String reason, String agent, long epoch) {
        this.status = status;
        this.reason = reason;
        this.agent = agent;
        this.epoch = epoch;
    }

    /**
     * @return The agent responsible setting this status
     */
    public String getAgent() {
        return agent;
    }

    /**
     * @return The reason for this status (e.g. 'incident INCXXX')
     */
    public String getReason() {
        return reason;
    }

    /**
     * @return The current status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return The epoch for when this status became active
     */
    public long getEpoch() {
        return epoch;
    }
}
