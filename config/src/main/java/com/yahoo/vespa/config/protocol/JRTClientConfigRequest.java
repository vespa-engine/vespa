// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

/**
 * Interface for config requests used by clients.
 *
 * @author Ulf Lilleengen
 */
public interface JRTClientConfigRequest extends JRTConfigRequest {

    /**
     * Validate config response given by the server. If none is given, or an error occurred, this should return false.
     *
     * @return true if valid response, false if not.
     */
    boolean validateResponse();

    /**
     * Test whether ot not the returned config has an updated generation. This should return false if no response have
     * been given.
     *
     * @return true if generation is updated, false if not.
     */
    boolean hasUpdatedGeneration();

    /**
     * Return the payload in the response given by the server. The payload will be empty if no response was given.
     *
     * @return the config payload.
     */
    Payload getNewPayload();

    /**
     * Create a new {@link JRTClientConfigRequest} based on this request based on the same request parameters,
     * but having the timeout changed.
     *
     * @param timeout server timeout of the new request.
     * @return a new {@link JRTClientConfigRequest} instance.
     */
    JRTClientConfigRequest nextRequest(long timeout);

    /**
     * Test whether or not the returned request is an error.
     *
     * @return true if error, false if not.
     */
    boolean isError();

    /**
     * Get the generation of the newly provided config. If none has been given, 0 should be returned.
     *
     * @return the new generation.
     */
    long getNewGeneration();

    /** Returns whether this config changes is due to an internal change not an application package change */
    boolean isInternalRedeploy();

    /**
     * Get the config md5 of the config returned by the server. Return an empty string if no response has been returned.
     *
     * @return a config md5.
     */
    String getNewConfigMd5();

    /**
     * Test whether or not the payload is contained in this response or not.
     * Should return false for error responses as well.
     *
     * @return true if empty, false if not.
     */
    boolean containsPayload();

    /**
     * Test whether or not the response contains an updated config or not.
     * False if no response has been returned.
     *
     * @return true if config is updated, false if not.
     */
    boolean hasUpdatedConfig();

    /**
     * Get the {@link Trace} given in the response by the server.
     * The {@link Trace} can be used to add further tracing and later printed to provide useful debug info.
     *
     * @return a {@link Trace}.
     */
    Trace getResponseTrace();

    /**
     * Get config definition content.
     *
     * @return def as lines.
     */
    DefContent getDefContent();

}
