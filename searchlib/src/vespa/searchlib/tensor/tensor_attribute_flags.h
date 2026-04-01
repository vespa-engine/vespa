// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::tensor {

/**
 * Flags for tensor attribute behavior.
 */
struct TensorAttributeFlags
{
    /*
     * Transitional setting to control if the nearest neighbor index uses a separate generation handler allowing for
     * shorter-lived generation guards for the index.
     *
     * false => use generations from attribute vector.
     * true  => use generation manager owned by nearest neighbor index.
     */
    static constexpr bool use_nearest_neighbor_index_generation_manager = false;
};

}
