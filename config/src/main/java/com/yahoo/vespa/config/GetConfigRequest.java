// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.vespa.config.protocol.DefContent;

import java.util.Optional;

/**
 * Interface for getConfig requests.
 * @author Ulf Lilleengen
 */
public interface GetConfigRequest {

    /**
     * Returns the ConfigKey for this request.
     *
     * @return the ConfigKey for this config request
     */
    ConfigKey<?> getConfigKey();

    /**
     * The def file contents in the request, or empty array if not sent/not supported
     *
     * @return the contents (payload) of the def schema
     */
    DefContent getDefContent();

    /**
     * Get Vespa version for this GetConfigRequest
     */
    Optional<com.yahoo.vespa.config.protocol.VespaVersion> getVespaVersion();

    /**
     * Whether or not the config can be retrieved from or stored in a cache.
     *
     * @return true if content should _not_ be cached, false if it should.
     */
    boolean noCache();

    /**
     * Returns the md5 of the config definition in the request.
     *
     * @return an md5 of config definition in request.
     */
    String getRequestDefMd5();

    /**
     * Returns the payload checksums from the config request.
     *
     * @return the payload checksums from request.
     */
    PayloadChecksums configPayloadChecksums();

}
