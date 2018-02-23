// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.compress.Compressor;

public class EncodedClusterStateBundle {

    private final Compressor.Compression compression;

    private EncodedClusterStateBundle(Compressor.Compression compression) {
        this.compression = compression;
    }

    public static EncodedClusterStateBundle fromCompressionBuffer(Compressor.Compression compression) {
        return new EncodedClusterStateBundle(compression);
    }

    public Compressor.Compression getCompression() {
        return compression;
    }

}
