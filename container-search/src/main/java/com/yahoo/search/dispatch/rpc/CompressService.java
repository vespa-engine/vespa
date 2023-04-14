// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;

/**
 * Implement interface to compress/decompress request/response
 *
 * @author baldersheim
 */
public class CompressService implements CompressPayload {
    /** The compression method which will be used with rpc dispatch. "lz4" (default) and "none" is supported. */
    public static final CompoundName dispatchCompression = CompoundName.from("dispatch.compression");
    private final Compressor compressor = new Compressor(CompressionType.LZ4, 5, 0.95, 256);


    @Override
    public Compressor.Compression compress(Query query, byte[] payload) {
        CompressionType compression = CompressionType.valueOf(query.properties().getString(dispatchCompression, "LZ4").toUpperCase());
        return compressor.compress(compression, payload);
    }

    @Override
    public byte[] decompress(Client.ProtobufResponse response) {
        CompressionType compression = CompressionType.valueOf(response.compression());
        return compressor.decompress(response.compressedPayload(), compression, response.uncompressedSize());
    }
    Compressor compressor() { return compressor; }
}
