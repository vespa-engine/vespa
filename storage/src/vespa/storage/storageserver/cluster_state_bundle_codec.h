// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "encoded_cluster_state_bundle.h"

namespace storage {

namespace lib { class ClusterStateBundle; }

/**
 * Provides opaque encoding and decoding of ClusterStateBundles for transmission over RPC.
 *
 * Implementations may choose to compress the encoded representation of the bundle.
 *
 * It is important that the input given to decode() is exactly equal to that given from
 * encode() for the results to be correct. Implementations must ensure that this information
 * is enough to losslessly reconstruct the full encoded ClusterStateBundle.
 */
class ClusterStateBundleCodec {
public:
    virtual ~ClusterStateBundleCodec() = default;

    virtual EncodedClusterStateBundle encode(const lib::ClusterStateBundle&) const = 0;
    virtual std::shared_ptr<const lib::ClusterStateBundle> decode(const EncodedClusterStateBundle&) const = 0;
};

}
