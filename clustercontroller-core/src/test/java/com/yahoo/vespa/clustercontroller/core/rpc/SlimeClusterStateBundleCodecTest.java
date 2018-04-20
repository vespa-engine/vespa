// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundleUtil;
import com.yahoo.vespa.clustercontroller.core.StateMapping;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;

public class SlimeClusterStateBundleCodecTest {

    private static ClusterStateBundle roundtripEncode(ClusterStateBundle stateBundle) {
        SlimeClusterStateBundleCodec codec = new SlimeClusterStateBundleCodec();
        EncodedClusterStateBundle encoded = codec.encode(stateBundle);
        return codec.decode(encoded);
    }

    private static ClusterStateBundle roundtripEncodeWithEnvelope(ClusterStateBundle stateBundle) {
        SlimeClusterStateBundleCodec codec = new SlimeClusterStateBundleCodec();
        byte[] encoded = codec.encodeWithEnvelope(stateBundle);
        return codec.decodeWithEnvelope(encoded);
    }

    @Test
    public void baseline_only_bundle_can_be_round_trip_encoded() {
        ClusterStateBundle stateBundle = ClusterStateBundleUtil.makeBundle("distributor:2 storage:2");
        assertThat(roundtripEncode(stateBundle), equalTo(stateBundle));
    }

    @Test
    public void multi_space_state_bundle_can_be_round_trip_encoded() {
        ClusterStateBundle stateBundle = ClusterStateBundleUtil.makeBundle("distributor:2 storage:2",
                StateMapping.of("default", "distributor:2 storage:2 .0.s:d"),
                StateMapping.of("upsidedown", "distributor:2 .0.s:d storage:2"));
        assertThat(roundtripEncode(stateBundle), equalTo(stateBundle));
    }

    private static ClusterStateBundle makeCompressableBundle() {
        StringBuilder allDownStates = new StringBuilder(2048);
        for (int i = 0; i < 99; ++i) {
            allDownStates.append(" .").append(i).append(".s:d");
        }
        // Exact same state set string repeated twice; perfect compression fodder.
        return ClusterStateBundleUtil.makeBundle(String.format("distributor:100%s storage:100%s",
                allDownStates.toString(), allDownStates.toString()));
    }

    @Test
    public void encoded_cluster_states_can_be_compressed() {
        ClusterStateBundle stateBundle = makeCompressableBundle();

        SlimeClusterStateBundleCodec codec = new SlimeClusterStateBundleCodec();
        EncodedClusterStateBundle encoded = codec.encode(stateBundle);
        assertThat(encoded.getCompression().data().length, lessThan(stateBundle.getBaselineClusterState().toString().length()));
        ClusterStateBundle decodedBundle = codec.decode(encoded);
        assertThat(decodedBundle, equalTo(stateBundle));
    }

    @Test
    public void uncompressed_enveloped_bundle_can_be_roundtrip_encoded() {
        // Insufficient length and too much entropy to be compressed
        ClusterStateBundle stateBundle = ClusterStateBundleUtil.makeBundle("distributor:2 storage:3");
        assertThat(roundtripEncodeWithEnvelope(stateBundle), equalTo(stateBundle));
    }

    @Test
    public void compressable_enveloped_bundle_can_be_roundtrip_encoded() {
        ClusterStateBundle stateBundle = makeCompressableBundle();
        assertThat(roundtripEncodeWithEnvelope(stateBundle), equalTo(stateBundle));
    }

}
