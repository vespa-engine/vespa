// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "cluster_state_bundle_codec.h"
#include <memory>

namespace storage {

/**
 * Implementation of ClusterStateBundleCodec which uses structured Slime binary encoding
 * to implement (de-)serialization of ClusterStateBundle instances. Encoding format is
 * intentionally extensible so that we may add other information to it later.
 *
 * LZ4 compression is transparently applied during encoding and decompression is
 * subsequently applied during decoding.
 */
class SlimeClusterStateBundleCodec : public ClusterStateBundleCodec {
public:
    EncodedClusterStateBundle encode(const lib::ClusterStateBundle&) const override;
    std::shared_ptr<const lib::ClusterStateBundle> decode(const EncodedClusterStateBundle&) const override;
};

}
