// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.slime.*;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vespa.clustercontroller.core.AnnotatedClusterState;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of ClusterStateBundleCodec which uses structured Slime binary encoding
 * to implement (de-)serialization of ClusterStateBundle instances. Encoding format is
 * intentionally extensible so that we may add other information to it later.
 *
 * LZ4 compression is transparently applied during encoding and decompression is
 * subsequently applied during decoding.
 */
public class SlimeClusterStateBundleCodec implements ClusterStateBundleCodec {

    private static final Compressor compressor = new Compressor(CompressionType.LZ4, 3, 0.90, 1024);

    @Override
    public EncodedClusterStateBundle encode(ClusterStateBundle stateBundle) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor states = root.setObject("states");
        // TODO add another function that is not toString for this..!
        states.setString("baseline", stateBundle.getBaselineClusterState().toString());
        Cursor spaces = states.setObject("spaces");
        stateBundle.getDerivedBucketSpaceStates().entrySet()
                .forEach(entry -> spaces.setString(entry.getKey(), entry.getValue().toString()));

        byte[] serialized = BinaryFormat.encode(slime);
        Compressor.Compression compression = compressor.compress(serialized);
        return EncodedClusterStateBundle.fromCompressionBuffer(compression);
    }

    @Override
    public ClusterStateBundle decode(EncodedClusterStateBundle encodedClusterStateBundle) {
        byte[] uncompressed = compressor.decompress(encodedClusterStateBundle.getCompression());
        Slime slime = BinaryFormat.decode(uncompressed);
        Inspector root = slime.get();
        Inspector states = root.field("states");
        ClusterState baseline = ClusterState.stateFromString(states.field("baseline").asString());

        Inspector spaces = states.field("spaces");
        Map<String, AnnotatedClusterState> derivedStates = new HashMap<>();
        spaces.traverse(((ObjectTraverser)(key, value) -> {
            derivedStates.put(key, AnnotatedClusterState.withoutAnnotations(ClusterState.stateFromString(value.asString())));
        }));

        return ClusterStateBundle.of(AnnotatedClusterState.withoutAnnotations(baseline), derivedStates);
    }
}
