// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.jrt.Request;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;

public class RPCUtil {

    public static ClusterStateBundle decodeStateBundleFromSetDistributionStatesRequest(Request req) {
        final CompressionType type = CompressionType.valueOf(req.parameters().get(0).asInt8());
        final int uncompressedSize = req.parameters().get(1).asInt32();
        final byte[] compressedPayload = req.parameters().get(2).asData();

        SlimeClusterStateBundleCodec codec = new SlimeClusterStateBundleCodec();
        Compressor.Compression compression = new Compressor.Compression(type, uncompressedSize, compressedPayload);
        return codec.decode(EncodedClusterStateBundle.fromCompressionBuffer(compression));
    }

}
