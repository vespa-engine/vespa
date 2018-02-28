// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.compress.Compressor;

/**
 * Contains an opaque encoded (possibly compressed) representation of a ClusterStateBundle.
 *
 * This bundle can in turn be sent over the wire or serialized by ensuring that all components
 * of the Compressor.Compression state can be reconstructed by the receiver. In practice this
 * means sending the Compression's <em>type</em>, <em>uncompressedSize</em> and <em>data</em>.
 */
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
