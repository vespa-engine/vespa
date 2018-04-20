// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;

/**
 * Cluster state bundle codec which opaquely encodes/decodes both the bundle
 * as well as the metadata required to correctly perform compression/decompression.
 *
 * Useful for embedding an opaque bundle blob somewhere without needing to care aboout
 * any of the associated metadata.
 */
public interface EnvelopedClusterStateBundleCodec {

    byte[] encodeWithEnvelope(ClusterStateBundle stateBundle);

    ClusterStateBundle decodeWithEnvelope(byte[] encodedClusterStateBundle);

}
