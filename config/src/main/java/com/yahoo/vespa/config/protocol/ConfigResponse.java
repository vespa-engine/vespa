// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.vespa.config.PayloadChecksum;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.text.AbstractUtf8Array;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A config response encapsulates the payload and some meta information. This makes it possible to
 * represent the payload in different formats all up to when rendering it to the client. A subclass
 * of this must be thread safe, because a response may be cached and, the methods below should be callable
 * from multiple request handler threads.
 *
 * @author Ulf Lilleengen
 */
public interface ConfigResponse {

    AbstractUtf8Array getPayload();

    long getGeneration();

    boolean applyOnRestart();

    String getConfigMd5();

    void serialize(OutputStream os, CompressionType uncompressed) throws IOException;

    default boolean hasEqualConfig(JRTServerConfigRequest request) {
        PayloadChecksums payloadChecksums = getPayloadChecksums();
        PayloadChecksum xxhash64 = payloadChecksums.getForType(PayloadChecksum.Type.XXHASH64);
        PayloadChecksum md5 = payloadChecksums.getForType(PayloadChecksum.Type.MD5);
        if (xxhash64 != null)
            return xxhash64.equals(request.getRequestConfigChecksums().getForType(PayloadChecksum.Type.XXHASH64));
        if (md5 != null)
            return md5.equals(request.getRequestConfigChecksums().getForType(PayloadChecksum.Type.MD5));

        return true;
    }

    default boolean hasNewerGeneration(JRTServerConfigRequest request) {
        return (getGeneration() > request.getRequestGeneration());
    }

    CompressionInfo getCompressionInfo();

    PayloadChecksums getPayloadChecksums();

}
