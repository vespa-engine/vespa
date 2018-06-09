// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.jrt.Request;
import com.yahoo.vespa.config.ConfigKey;

import java.util.Optional;

/**
 * Common interface for jrt config requests available both at server and client.
 * @author Ulf Lilleengen
 */
public interface JRTConfigRequest {
    /**
     * Get the config key of the config request.
     * @return a {@link ConfigKey}.
     */
    ConfigKey<?> getConfigKey();

    /**
     * Perform request parameter validation of this config request. This method should be called before fetching
     * any kind of config protocol-specific parameter.
     * @return true if valid, false if not.
     */
    boolean validateParameters();

    /**
     * Get the config md5 of the config request. Return an empty string if no response has been returned.
     * @return a config md5.
     */
    String getRequestConfigMd5();

    /**
     * Get the generation of the requested config. If none has been given, 0 should be returned.
     * @return the generation in the request.
     */
    long getRequestGeneration();

    /**
     * Get the JRT request object for this config request.
     * TODO: This method leaks the internal jrt stuff :(
     * @return a {@link Request} object.
     */
    Request getRequest();

    /**
     * Get a short hand description of this request.
     * @return a short description
     */
    String getShortDescription();

    /**
     * Get the error code of this request
     * @return the error code as defined in {@link com.yahoo.vespa.config.ErrorCode}.
     */
    int errorCode();

    /**
     * Return the error message of this request, mostly corresponding to the {@link com.yahoo.vespa.config.ErrorCode}.
     * @return the error message.
     */
    String errorMessage();

    /**
     * Get the server timeout of this request.
     * @return the timeout given to the server
     */
    long getTimeout();

    /**
     * Get the config protocol version
     * @return a protocol version number.
     */
    long getProtocolVersion();

    /**
     * Get the wanted generation for this request.
     * @return a generation that client would like.
     */
    long getWantedGeneration();

    /**
     * Get the host name of the client that is requesting config.
     * @return hostname of the client.
     */
    String getClientHostName();

    /**
     * Get the Vespa version of the client that initiated the request
     * @return Vespa version of the client
     */
    Optional<VespaVersion> getVespaVersion();
}
