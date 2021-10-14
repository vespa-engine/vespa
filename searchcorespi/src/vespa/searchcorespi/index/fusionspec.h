// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <vector>

namespace searchcorespi::index {

/**
 * Specifies a set of disk index ids for fusion.
 *
 * Note: All ids in FusionSpec are absolute ids.
 **/
struct FusionSpec {
    uint32_t last_fusion_id;
    std::vector<uint32_t> flush_ids;

    FusionSpec() : last_fusion_id(0), flush_ids() {}
};

}
