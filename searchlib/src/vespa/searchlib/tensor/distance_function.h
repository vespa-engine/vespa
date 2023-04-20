// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/eval/eval/cell_type.h>

namespace vespalib::eval { struct TypedCells; }

namespace search::tensor {

class DistanceConverter {
public:
    virtual ~DistanceConverter() = default;

    // convert threshold (external distance units) to internal units
    virtual double convert_threshold(double threshold) const = 0;

    // convert internal distance to rawscore (1.0 / (1.0 + d))
    virtual double to_rawscore(double distance) const = 0;
};

/**
 * Interface used to calculate the distance between two n-dimensional vectors.
 *
 * The vectors must be of same size and same cell type (float or double).
 * The actual implementation must know which type the vectors are.
 */
class DistanceFunction : public DistanceConverter {
private:
    vespalib::eval::CellType _expect_cell_type;
public:
    using UP = std::unique_ptr<DistanceFunction>;

    DistanceFunction(vespalib::eval::CellType expected) : _expect_cell_type(expected) {}

    virtual ~DistanceFunction() = default;

    // input (query) vectors must be converted to this cell type:
    vespalib::eval::CellType expected_cell_type() const {
        return _expect_cell_type;
    }

    // calculate internal distance (comparable)
    virtual double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const = 0;

    // calculate internal distance, early return allowed if > limit
    virtual double calc_with_limit(const vespalib::eval::TypedCells& lhs,
                                   const vespalib::eval::TypedCells& rhs,
                                   double limit) const = 0;
};

}
