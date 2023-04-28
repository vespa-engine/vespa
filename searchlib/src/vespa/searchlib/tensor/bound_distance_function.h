// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/eval/eval/cell_type.h>
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/vespalib/util/arrayref.h>
#include "distance_function.h"

namespace vespalib::eval { struct TypedCells; }

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

    BoundDistanceFunction() = default;

    virtual ~BoundDistanceFunction() = default;

    // calculate internal distance (comparable)
    virtual double calc(const vespalib::eval::TypedCells& rhs) const = 0;

    // calculate internal distance, early return allowed if > limit
    virtual double calc_with_limit(const vespalib::eval::TypedCells& rhs,
                                   double limit) const = 0;
};

}
