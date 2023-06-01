// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/eval/eval/cell_type.h>

namespace vespalib::eval { struct TypedCells; }

namespace search::tensor {

class DistanceConverter {
public:
    virtual ~DistanceConverter() = default;

    /**
     * Convert threshold (external distance units) to internal units.
     */
    virtual double convert_threshold(double threshold) const = 0;

    /**
     * Convert internal distance to rawscore (also used as closeness).
     */
    virtual double to_rawscore(double distance) const = 0;

    /**
     * Convert rawscore to external distance.
     * Override this when the rawscore is NOT defined as (1.0 / (1.0 + external_distance)).
     */
    virtual double to_distance(double rawscore) const {
        return (1.0 / rawscore) - 1.0;
    }

    /**
     * The minimum rawscore (also used as closeness) that this distance function can return.
     */
    virtual double min_rawscore() const {
        return 0.0;
    }
};

}
