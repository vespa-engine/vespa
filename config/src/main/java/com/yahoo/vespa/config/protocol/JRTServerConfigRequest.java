// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.vespa.config.GetConfigRequest;

/**
 * Interface for config requests at the server end point.
 *
 * @author Ulf Lilleengen
 */
public interface JRTServerConfigRequest extends JRTConfigRequest, GetConfigRequest {

    /**
     * Notify this request that its delayed due to no new config being available at this point. The value
     * provided in this function should be returned when calling {@link #isDelayedResponse()}.
     *
     * @param delayedResponse true if response is delayed, false if not.
     */
    void setDelayedResponse(boolean delayedResponse);

    /**
     * Signal error when handling this request. The error should be reflected in the request state and propagated
     * back to the client.
     *
     * @param errorCode error code, as described in {@link com.yahoo.vespa.config.ErrorCode}.
     * @param message message to display for this error, typically printed by client.
     */
    void addErrorResponse(int errorCode, String message);

    /**
     * Signal that the request was handled and provide return values typically needed by a client.
     *
     * @param payload The config payload that the client should receive.
     * @param generation The config generation of the given payload.
     * @param internalRedeployment whether this payload was generated from an internal redeployment not an
     *                             application package change
     * @param configMd5 The md5sum of the given payload.
     */
    void addOkResponse(Payload payload, long generation, boolean internalRedeployment, String configMd5);

    /**
     * Get the current config md5 of the client config.
     *
     * @return a config md5.
     */
    String getRequestConfigMd5();

    /**
     * Get the current config generation of the client config.
     *
     * @return the current config generation.
     */
    long getRequestGeneration();

    /**
     * Check whether or not this request is delayed.
     *
     * @return true if delayed, false if not.
     */
    boolean isDelayedResponse();

    /**
     * Returns whether the response config was created by a system internal redeploy, not an application
     * package change
     */
    boolean isInternalRedeploy();

    /**
     * Get the request trace for this request. The trace can be used to trace config execution to provide useful
     * debug info in production environments.
     *
     * @return a {@link Trace} instance.
     */
    Trace getRequestTrace();

    /**
     * Extract the appropriate payload for this request type for a given config response.
     *
     * @param response {@link ConfigResponse} to get payload from.
     * @return A {@link Payload} that satisfies this request format.
     */
    Payload payloadFromResponse(ConfigResponse response);

}
