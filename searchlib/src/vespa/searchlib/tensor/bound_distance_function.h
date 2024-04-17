// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include <vespa/eval/eval/typed_cells.h>

namespace search::tensor {

/**
 * Interface used to calculate the distance from a prebound n-dimensional vector.
 *
 * Use from a single thread only - not required to be thread safe.
 * The actual implementation may keep state about the prebound vector and
 * mutable temporary storage.
 */
class BoundDistanceFunction : public DistanceConverter {
public:
    using UP = std::unique_ptr<BoundDistanceFunction>;
    using TypedCells = vespalib::eval::TypedCells;

    BoundDistanceFunction() noexcept = default;

    ~BoundDistanceFunction() override = default;

    // calculate internal distance (comparable)
    virtual double calc(TypedCells rhs) const noexcept = 0;

    // calculate internal distance, early return allowed if > limit
    virtual double calc_with_limit(TypedCells rhs, double limit) const noexcept = 0;
};

}
