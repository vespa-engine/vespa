// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.compress.Compressor;
import com.yahoo.search.Query;

/**
 * Interface for compressing and decompressing request/response
 *
 * @author baldersheim
 */
public interface CompressPayload {
    Compressor.Compression compress(Query query, byte[] payload);
    byte[] decompress(Client.ProtobufResponse response);
}
