// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.text.Utf8Array;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * A config response encapsulates the payload and some meta information. This makes it possible to
 * represent the payload in different formats all up to when rendering it to the client. A subclass
 * of this must be thread safe, because a response may be cached and, the methods below should be callable
 * from multiple request handler threads.
 *
 * @author Ulf Lilleengen
 */
public interface ConfigResponse {

    Utf8Array getPayload();

    List<String> getLegacyPayload();

    long getGeneration();

    boolean isInternalRedeploy();

    String getConfigMd5();

    void serialize(OutputStream os, CompressionType uncompressed) throws IOException;

    default boolean hasEqualConfig(JRTServerConfigRequest request) {
        return (getConfigMd5().equals(request.getRequestConfigMd5()));
    }

    default boolean hasNewerGeneration(JRTServerConfigRequest request) {
        return (getGeneration() > request.getRequestGeneration());
    }

    CompressionInfo getCompressionInfo();

}
